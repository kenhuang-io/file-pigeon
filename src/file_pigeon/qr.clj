(ns file-pigeon.qr
  (:import [com.google.zxing BarcodeFormat EncodeHintType]
           [com.google.zxing.qrcode QRCodeWriter]
           [com.google.zxing.qrcode.decoder ErrorCorrectionLevel]
           [com.google.zxing.client.j2se MatrixToImageWriter]
           [java.io ByteArrayOutputStream]))

(defn- generate-bit-matrix
  "Generates a ZXing BitMatrix for the given text with specified margin."
  ([text]
   (generate-bit-matrix text 0 0 1))
  ([text width height margin]
   (let [writer (QRCodeWriter.)
         hints {EncodeHintType/MARGIN (int margin)
                EncodeHintType/ERROR_CORRECTION ErrorCorrectionLevel/M}]
     (.encode writer text BarcodeFormat/QR_CODE (int width) (int height) hints))))

(defn to-terminal-string
  "Renders a compact, high-contrast QR code string using Unicode half-block characters (▀, ▄, █, space).
   Each row in the terminal represents 2 vertical QR modules, making it compact and legible."
  [text]
  (try
    (let [matrix (generate-bit-matrix text 0 0 1)
          w (.getWidth matrix)
          h (.getHeight matrix)
          sb (StringBuilder.)]
      (doseq [y (range 0 h 2)]
        (doseq [x (range w)]
          (let [top (.get matrix x y)
                bottom (if (< (inc y) h) (.get matrix x (inc y)) false)]
            (.append sb
                     (cond
                       (and top bottom) " "
                       top "▄"
                       bottom "▀"
                       :else "█"))))
        (.append sb "\n"))
      (str sb))
    (catch Exception e
      (str "Unable to render QR code: " (.getMessage e)))))

(defn to-png-bytes
  "Generates a PNG image of the QR code as a byte array."
  ([text]
   (to-png-bytes text 256))
  ([text size]
   (let [matrix (generate-bit-matrix text size size 2)
         baos (ByteArrayOutputStream.)]
     (MatrixToImageWriter/writeToStream matrix "PNG" baos)
     (.toByteArray baos))))
