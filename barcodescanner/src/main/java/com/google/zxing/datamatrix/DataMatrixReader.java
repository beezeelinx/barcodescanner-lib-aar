/*
 * Copyright 2007 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.datamatrix;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.datamatrix.decoder.Decoder;
import com.google.zxing.datamatrix.detector.Detector;

import java.util.List;
import java.util.Map;

/**
 * This implementation can detect and decode Data Matrix codes in an image.
 *
 * @author bbrown@google.com (Brian Brown)
 */
public final class DataMatrixReader implements Reader {

  private static final ResultPoint[] NO_POINTS = new ResultPoint[0];

  private final Decoder decoder = new Decoder();

  private class DetectorDecoder {
    public DecoderResult decoderResult;
    public ResultPoint[] points;

    public DetectorDecoder(DecoderResult decoderResult, ResultPoint[] points) {
      this.decoderResult = decoderResult;
      this.points = points;
    }
  }

  /**
   * Locates and decodes a Data Matrix code in an image.
   *
   * @return a String representing the content encoded by the Data Matrix code
   * @throws NotFoundException if a Data Matrix code cannot be found
   * @throws FormatException   if a Data Matrix code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  @Override
  public Result decode(BinaryBitmap image) throws NotFoundException, ChecksumException, FormatException {
    return decode(image, null);
  }

  @Override
  public Result decode(BinaryBitmap image, Map<DecodeHintType, ?> hints)
          throws NotFoundException, ChecksumException, FormatException {
    DecoderResult decoderResult = null;
    ResultPoint[] points = null;
    NotFoundException lastError1 = null;
    ChecksumException lastError2 = null;
    FormatException lastError3 = null;

    if (hints != null && hints.containsKey(DecodeHintType.PURE_BARCODE)) {
      BitMatrix bits = extractPureBits(image.getBlackMatrix());
      decoderResult = decoder.decode(bits);
      points = NO_POINTS;
    } else {
      int initSize = 10;

      int maxWidthSplit = image.getWidth() / initSize;
      int maxHeightSplit = image.getHeight() / initSize;
      for (int split = 1; split <= 5; split += 2) {
        try {
          DetectorDecoder result = getDecoderResultSplit(image, initSize, Math.min(split, maxWidthSplit), Math.min(split, maxHeightSplit));
          points = result.points;
          decoderResult = result.decoderResult;
          break;
        } catch (NotFoundException error) {
          lastError1 = error;
          lastError2 = null;
          lastError3 = null;
        } catch (ChecksumException error) {
          lastError1 = null;
          lastError2 = error;
          lastError3 = null;
        } catch (FormatException error) {
          lastError1 = null;
          lastError2 = null;
          lastError3 = error;
        }
      }

//      if (decoderResult == null) {
//        // Try harder
//        for (int x = initSize / 2; x < (image.getWidth() - initSize / 2); x += initSize) {
//          for (int y = initSize / 2; y < (image.getHeight() - initSize / 2); y += initSize) {
//            try {
//              DetectorDecoder result = getDecoderResult(image, initSize, x, y);
//              points = result.points;
//              decoderResult = result.decoderResult;
//              break;
//            } catch (NotFoundException error) {
//              lastError1 = error;
//              lastError2 = null;
//              lastError3 = null;
//            } catch (ChecksumException error) {
//              lastError1 = null;
//              lastError2 = error;
//              lastError3 = null;
//            } catch (FormatException error) {
//              lastError1 = null;
//              lastError2 = null;
//              lastError3 = error;
//            }
//          }
//        }
//      }

      if (decoderResult == null) {
        if (lastError1 != null) {
          throw lastError1;
        }
        if (lastError2 != null) {
          throw lastError2;
        }
        if (lastError3 != null) {
          throw lastError3;
        }
        throw NotFoundException.getNotFoundInstance();
      }
    }

    Result result = new Result(decoderResult.getText(), decoderResult.getRawBytes(), points,
            BarcodeFormat.DATA_MATRIX);
    List<byte[]> byteSegments = decoderResult.getByteSegments();
    if (byteSegments != null) {
      result.putMetadata(ResultMetadataType.BYTE_SEGMENTS, byteSegments);
    }
    String ecLevel = decoderResult.getECLevel();
    if (ecLevel != null) {
      result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL, ecLevel);
    }
    result.putMetadata(ResultMetadataType.SYMBOLOGY_IDENTIFIER, "]d" + decoderResult.getSymbologyModifier());
    return result;
  }

  @Override
  public void reset() {
    // do nothing
  }

  private DetectorDecoder getDecoderResult(BinaryBitmap image, int initSize, int x, int y) throws
          NotFoundException, ChecksumException, FormatException {
    DetectorResult detectorResult = new Detector(image.getBlackMatrix(), initSize, x, y).detect();
    DecoderResult decoderResult = decoder.decode(detectorResult.getBits());
    ResultPoint[] points = detectorResult.getPoints();
    return new DetectorDecoder(decoderResult, points);
  }

  private DetectorDecoder getDecoderResultSplit(BinaryBitmap image, int initSize,
                                                int splitWidth, int splitHeight) throws
          NotFoundException, ChecksumException, FormatException {
    NotFoundException lastError1 = null;
    ChecksumException lastError2 = null;
    FormatException lastError3 = null;

    int widthSplit = image.getWidth() / splitWidth;
    int heightSplit = image.getHeight() / splitHeight;
    for (int x = widthSplit / 2; x < image.getWidth(); x += widthSplit) {
      for (int y = heightSplit / 2; y < image.getHeight(); y += heightSplit) {
        try {
          return getDecoderResult(image, initSize, x, y);
        } catch (NotFoundException error) {
          lastError1 = error;
          lastError2 = null;
          lastError3 = null;
        } catch (ChecksumException error) {
          lastError1 = null;
          lastError2 = error;
          lastError3 = null;
        } catch (FormatException error) {
          lastError1 = null;
          lastError2 = null;
          lastError3 = error;
        }
      }
    }

    if (lastError1 != null) {
      throw lastError1;
    }
    if (lastError2 != null) {
      throw lastError2;
    }
    if (lastError3 != null) {
      throw lastError3;
    }

    throw NotFoundException.getNotFoundInstance();
  }

  /**
   * This method detects a code in a "pure" image -- that is, pure monochrome image
   * which contains only an unrotated, unskewed, image of a code, with some white border
   * around it. This is a specialized method that works exceptionally fast in this special
   * case.
   */
  private static BitMatrix extractPureBits(BitMatrix image) throws NotFoundException {

    int[] leftTopBlack = image.getTopLeftOnBit();
    int[] rightBottomBlack = image.getBottomRightOnBit();
    if (leftTopBlack == null || rightBottomBlack == null) {
      throw NotFoundException.getNotFoundInstance();
    }

    int moduleSize = moduleSize(leftTopBlack, image);

    int top = leftTopBlack[1];
    int bottom = rightBottomBlack[1];
    int left = leftTopBlack[0];
    int right = rightBottomBlack[0];

    int matrixWidth = (right - left + 1) / moduleSize;
    int matrixHeight = (bottom - top + 1) / moduleSize;
    if (matrixWidth <= 0 || matrixHeight <= 0) {
      throw NotFoundException.getNotFoundInstance();
    }

    // Push in the "border" by half the module width so that we start
    // sampling in the middle of the module. Just in case the image is a
    // little off, this will help recover.
    int nudge = moduleSize / 2;
    top += nudge;
    left += nudge;

    // Now just read off the bits
    BitMatrix bits = new BitMatrix(matrixWidth, matrixHeight);
    for (int y = 0; y < matrixHeight; y++) {
      int iOffset = top + y * moduleSize;
      for (int x = 0; x < matrixWidth; x++) {
        if (image.get(left + x * moduleSize, iOffset)) {
          bits.set(x, y);
        }
      }
    }
    return bits;
  }

  private static int moduleSize(int[] leftTopBlack, BitMatrix image) throws
          NotFoundException {
    int width = image.getWidth();
    int x = leftTopBlack[0];
    int y = leftTopBlack[1];
    while (x < width && image.get(x, y)) {
      x++;
    }
    if (x == width) {
      throw NotFoundException.getNotFoundInstance();
    }

    int moduleSize = x - leftTopBlack[0];
    if (moduleSize == 0) {
      throw NotFoundException.getNotFoundInstance();
    }
    return moduleSize;
  }

}
