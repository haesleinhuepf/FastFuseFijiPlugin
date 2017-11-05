package de.mpicbg.rhaase.fastfuse;

import clearcl.ClearCLContext;
import clearcl.ClearCLImage;
import clearcl.enums.ImageChannelDataType;
import clearcontrol.core.concurrent.executors.AsynchronousExecutorFeature;
import clearcontrol.stack.ContiguousOffHeapPlanarStackFactory;
import clearcontrol.stack.StackInterface;
import clearcontrol.stack.StackRequest;
import clearcontrol.stack.sourcesink.sink.RawFileStackSink;
import clearcontrol.stack.sourcesink.source.RawFileStackSource;
import coremem.recycling.BasicRecycler;
import fastfuse.FastFusionEngine;
import fastfuse.FastFusionEngineInterface;
import fastfuse.FastFusionMemoryPool;
import fastfuse.registration.AffineMatrix;
import fastfuse.tasks.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Author: Robert Haase (http://haesleinhuepf.net) at MPI CBG (http://mpi-cbg.de)
 * November 2017
 */
public class FastFusion extends FastFusionEngine implements
                                                 FastFusionEngineInterface,
                                                 AsynchronousExecutorFeature,
                                                 RegistrationListener

{
  public FastFusion(ClearCLContext pContext)
  {
    super(pContext);
  }

  private ClearCLContext mContext;

  private boolean mRegistration = true;

  private boolean mDownscale = true;

  private double mMemRatio = 0.8;

  private boolean mSubtractBackground = false;

  private int stackIndex = 0;

  private File rootFolder;

  private String datasetName = "none";

  private String[]
      names =
      { "C0L0",
        "C0L1",
        "C0L2",
        "C0L3",
        "C1L0",
        "C1L1",
        "C1L2",
        "C1L3" };

  private RegistrationTask mRegistrationTask;

  public void run() throws IOException
  {
    assert rootFolder != null;
    assert rootFolder.isDirectory();

    long
        lMaxMemoryInBytes =
        (long) (mMemRatio * mContext.getDevice()
                                    .getGlobalMemorySizeInBytes());
    FastFusionMemoryPool.getInstance(mContext, lMaxMemoryInBytes);

    int[] lKernelSizesRegistration = new int[] { 3, 3, 3 };
    float[]
        lKernelSigmasRegistration =
        new float[] { 0.5f, 0.5f, 0.5f };

    float[] lKernelSigmasFusion = new float[] { 15, 15, 5 };

    float[] lKernelSigmasBackground = new float[] { 30, 30, 10 };

    if (mDownscale)
      addTasks(DownsampleXYbyHalfTask.applyAndReleaseInputs(
          DownsampleXYbyHalfTask.Type.Median,
          "d",
          "C0L0",
          "C0L1",
          "C0L2",
          "C0L3",
          "C1L0",
          "C1L1",
          "C1L2",
          "C1L3"));
    else
      addTasks(IdentityTask.withSuffix("d",
                                       "C0L0",
                                       "C0L1",
                                       "C0L2",
                                       "C0L3",
                                       "C1L0",
                                       "C1L1",
                                       "C1L2",
                                       "C1L3"));

    ImageChannelDataType
        lInitialFusionDataType =
        mRegistration ?
        ImageChannelDataType.Float :
        ImageChannelDataType.UnsignedInt16;

    addTasks(CompositeTasks.fuseWithSmoothWeights("C0",
                                                  lInitialFusionDataType,
                                                  lKernelSigmasFusion,
                                                  true,
                                                  "C0L0d",
                                                  "C0L1d",
                                                  "C0L2d",
                                                  "C0L3d"));

    addTasks(CompositeTasks.fuseWithSmoothWeights("C1",
                                                  lInitialFusionDataType,
                                                  lKernelSigmasFusion,
                                                  true,
                                                  "C1L0d",
                                                  "C1L1d",
                                                  "C1L2d",
                                                  "C1L3d"));

    if (mRegistration)
    {
      List<TaskInterface>
          lRegistrationTaskList =
          CompositeTasks.registerWithBlurPreprocessing("C0",
                                                       "C1",
                                                       "C1adjusted",
                                                       lKernelSigmasRegistration,
                                                       lKernelSizesRegistration,
                                                       AffineMatrix.scaling(
                                                           -1,
                                                           1,
                                                           1),
                                                       true);
      addTasks(lRegistrationTaskList);
      // extract registration task from list
      for (TaskInterface lTask : lRegistrationTaskList)
        if (lTask instanceof RegistrationTask)
        {
          mRegistrationTask = (RegistrationTask) lTask;
          break;
        }
    }
    else
    {
      addTask(FlipTask.flipX("C1", "C1adjusted"));
      addTask(new MemoryReleaseTask("C1adjusted", "C1"));
    }

    // addTasks(CompositeTasks.fuseWithSmoothWeights("fused",
    // ImageChannelDataType.UnsignedInt16,
    // pKernelSigmasFusion,
    // true,
    // "C0",
    // "C1adjusted"));

    if (mSubtractBackground)
    {
      addTasks(CompositeTasks.fuseWithSmoothWeights(
          "fused-preliminary",
          ImageChannelDataType.Float,
          lKernelSigmasFusion,
          true,
          "C0",
          "C1adjusted"));

      addTasks(CompositeTasks.subtractBlurredCopyFromFloatImage(
          "fused-preliminary",
          "fused",
          lKernelSigmasBackground,
          true,
          ImageChannelDataType.UnsignedInt16));
    }
    else
    {
      addTasks(CompositeTasks.fuseWithSmoothWeights("fused",
                                                    ImageChannelDataType.Float,
                                                    lKernelSigmasFusion,
                                                    true,
                                                    "C0",
                                                    "C1adjusted"));
    }

    BasicRecycler<StackInterface, StackRequest>
        stackRecycler =
        new BasicRecycler(new ContiguousOffHeapPlanarStackFactory(),
                          10,
                          10,
                          true);
    RawFileStackSource
        rawFileStackSource =
        new RawFileStackSource(stackRecycler);

    for (int i = 0; i < names.length; i++)
    {
      rawFileStackSource.setLocation(rootFolder, datasetName);
      StackInterface
          stack =
          rawFileStackSource.getStack(names[i], stackIndex);
      passImage(datasetName, (ClearCLImage) stack);
    }
    this.executeAllTasks();
    RawFileStackSink sink = new RawFileStackSink();
    sink.setLocation(rootFolder, datasetName);
    sink.appendStack((StackInterface) this.getImage("fused"));
    sink.close();
  }

  @Override public void newComputedTheta(double[] doubles)
  {
    System.out.println("newComputedTheta: "
                       + Arrays.toString(doubles));
  }

  @Override public void newUsedTheta(double[] doubles)
  {
    System.out.println("newUsedTheta: " + Arrays.toString(doubles));
  }

  @Override public void notifyListenersOfNewScoreForComputedTheta(
      double v)
  {
    System.out.println("notifyListenersOfNewScoreForComputedTheta: ");
  }

  @Override public void notifyListenersOfNewScoreForUsedTheta(double v)
  {
    System.out.println("notifyListenersOfNewScoreForUsedTheta: " + v);
  }

  public void setStackIndex(int stackIndex)
  {
    this.stackIndex = stackIndex;
  }

  public void setRegistration(boolean mRegistration)
  {
    this.mRegistration = mRegistration;
  }

  public void setDownscale(boolean mDownscale)
  {
    this.mDownscale = mDownscale;
  }

  public void setMemRatio(double mMemRatio)
  {
    this.mMemRatio = mMemRatio;
  }

  public void setSubtractBackground(boolean mSubtractBackground)
  {
    this.mSubtractBackground = mSubtractBackground;
  }

  public void setRootFolder(File rootFolder)
  {
    this.rootFolder = rootFolder;
  }

  public void setDatasetName(String datasetName)
  {
    this.datasetName = datasetName;
  }

  public void setNames(String[] names)
  {
    this.names = names;
  }

}
