package de.mpicbg.rhaase;

import de.mpicbg.rhaase.scijava.FastFuseFijiPlugin;
import ij.ImagePlus;
import net.imagej.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;

import java.util.Random;

public class Main
{
  public static void main(final String... args) throws Exception
  {
    // Run ImageJ
    final ImageJ ij = new ImageJ();
    ij.ui().showUI();

    // Create test data
    int size = 256;

    Img<FloatType> img = ArrayImgs.floats(new long[] { size, size });

    Cursor<FloatType> cursor = img.cursor();
    Random random = new Random();
    while (cursor.hasNext())
    {
      cursor.next().set(random.nextFloat() * 65536);
    }
    ImagePlus imp = ImageJFunctions.wrap(img, "temp");

    Object[]
        imglibParameters =
        new Object[] { "currentData",
                       img,
                       "translateX",
                       5,
                       "translateY",
                       15};

    ij.ui().show(img);

    ij.command().run(FastFuseFijiPlugin.class, true, imglibParameters);
  }
}
