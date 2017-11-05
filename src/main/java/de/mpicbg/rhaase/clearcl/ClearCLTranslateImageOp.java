package de.mpicbg.rhaase.clearcl;

import clearcl.*;
import clearcl.enums.HostAccessType;
import clearcl.enums.ImageChannelDataType;
import clearcl.enums.ImageChannelOrder;
import clearcl.enums.KernelAccessType;
import clearcl.ops.OpsBase;
import net.haesleinhuepf.clearcl.utilities.ImageCache;

import java.io.IOException;

public class ClearCLTranslateImageOp extends OpsBase
{
  private ClearCLKernel mTranslateImageKernel2F;

  private ClearCLContext mContext;

  private ImageCache mOutputImageCache;

  private int translationX = 0;
  private int translationY = 0;

  public ClearCLTranslateImageOp(ClearCLQueue pClearCLQueue) throws
                                                         IOException
  {
    super(pClearCLQueue);
    mContext = getContext();
    mOutputImageCache = new ImageCache(mContext);

    ClearCLProgram
        lConvolutionProgram =
        getContext().createProgram(ClearCLTranslateImageOp.class,
                                   "imageTranslation.cl");

    lConvolutionProgram.addBuildOptionAllMathOpt();
    lConvolutionProgram.addDefine("FLOAT");
    lConvolutionProgram.buildAndLog();

    mTranslateImageKernel2F =
        lConvolutionProgram.createKernel("translate_image_2F");
  }

  public ClearCLImage translate(ClearCLImage input)
  {
    ClearCLImage
        output =
        mOutputImageCache.get2DImage(HostAccessType.ReadWrite,
                                     KernelAccessType.ReadWrite,
                                     ImageChannelOrder.Intensity,
                                     ImageChannelDataType.Float,
                                     input.getWidth(),
                                     input.getHeight());

    mTranslateImageKernel2F.setArgument("input", input);
    mTranslateImageKernel2F.setArgument("translation_x", translationX);
    mTranslateImageKernel2F.setArgument("translation_y", translationY);
    mTranslateImageKernel2F.setArgument("output", output);
    mTranslateImageKernel2F.setGlobalSizes(input.getDimensions());
    mTranslateImageKernel2F.run();

    return output;
  }

  public void setTranslationX(int translationX)
  {
    this.translationX = translationX;
  }

  public void setTranslationY(int translationY)
  {
    this.translationY = translationY;
  }
}
