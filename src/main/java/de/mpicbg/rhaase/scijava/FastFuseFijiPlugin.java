package de.mpicbg.rhaase.scijava;

import clearcl.*;
import clearcl.backend.ClearCLBackendInterface;
import clearcl.backend.javacl.ClearCLBackendJavaCL;
import clearcl.enums.ImageChannelDataType;
import clearcontrol.core.concurrent.executors.AsynchronousExecutorFeature;
import clearcontrol.core.configuration.MachineConfiguration;
import clearcontrol.microscope.stacks.StackRecyclerManager;
import clearcontrol.stack.ContiguousOffHeapPlanarStackFactory;
import clearcontrol.stack.StackInterface;
import clearcontrol.stack.StackRequest;
import clearcontrol.stack.sourcesink.source.RawFileStackSource;
import coremem.recycling.BasicRecycler;
import coremem.recycling.RecyclerInterface;
import de.mpicbg.rhaase.fastfuse.FastFusion;
import fastfuse.FastFusionEngine;
import fastfuse.FastFusionEngineInterface;
import fastfuse.FastFusionMemoryPool;
import fastfuse.registration.AffineMatrix;
import fastfuse.tasks.*;
import net.haesleinhuepf.clearcl.utilities.ClearCLImageImgConverter;
import de.mpicbg.rhaase.clearcl.ClearCLTranslateImageOp;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Plugin(type = Command.class, menuPath = "XWing>Postprocessing>FastFuse")
public class FastFuseFijiPlugin implements
    Command
{
  @Parameter int stackIndex = 0;
  @Parameter(style="directory") File rootFolder;
  @Parameter String datasetName;
  @Parameter String names="C0L0,C0L1,C0L2,C0L3,C1L0,C1L1,C1L2,C1L3";

  @Parameter boolean subtractBackground = false;
  @Parameter boolean registration = true;
  @Parameter boolean downscale = true;
  @Parameter double memRatio = 0.8;


  @Override public void run()
  {
    ClearCLBackendInterface
        lClearCLBackend =
        new ClearCLBackendJavaCL();

    try (ClearCL lClearCL = new ClearCL(lClearCLBackend))
    {
      ClearCLDevice lBestGPUDevice = lClearCL.getBestGPUDevice();

      ClearCLContext lContext = lBestGPUDevice.createContext();
      FastFusion fastFusion = new FastFusion(lContext);

      fastFusion.setStackIndex(stackIndex);
      fastFusion.setRegistration(registration);
      fastFusion.setDownscale(downscale);
      fastFusion.setMemRatio(memRatio);
      fastFusion.setSubtractBackground(subtractBackground);
      fastFusion.setRootFolder(rootFolder);
      fastFusion.setDatasetName(datasetName);
      fastFusion.setNames(names.split(","));
      fastFusion.run();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
  }
}
