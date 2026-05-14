package smartfarm.ui.platform;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Minimal pure-Java PNG encoder for JavaFX {@link Image}s.
 *
 * <p>Substrate-safe: uses only {@code java.io}, {@code java.util.zip},
 * and {@code java.nio.charset} from {@code java.base}. Avoids
 * {@code javax.imageio} (in {@code java.desktop}) and
 * {@code javafx.embed.swing.SwingFXUtils} (in {@code javafx.swing}) —
 * neither is available on the GraalVM Android Substrate compile
 * classpath.
 *
 * <p>Output format: PNG signature + IHDR + single IDAT + IEND.
 * Color type 6 (truecolor + alpha), bit depth 8, filter method
 * "None" (filter byte 0) on every scanline. Deflated with the default
 * compression level.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@link PlatformPickers} — persists a picked Android gallery image
 *       to private storage so {@code PlantIdService.analyzeImage(String)}
 *       has a real path.</li>
 *   <li>{@code smartfarm.ui.tools.LauncherIconGenerator} (B7) — writes
 *       the 10 Android launcher icons.</li>
 * </ul>
 */
public final class PngEncoder {

    private PngEncoder() {}

    /**
     * Encodes the given JavaFX image to a self-contained PNG byte array.
     *
     * @param img the source image (must have a {@link PixelReader} and
     *            non-zero dimensions).
     * @return the PNG file bytes, ready to be written to disk.
     * @throws IOException if the image cannot be read or the PNG bytes
     *                     cannot be assembled.
     */
    public static byte[] encode(Image img) throws IOException {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        if (w <= 0 || h <= 0) throw new IOException("Empty image");
        PixelReader pr = img.getPixelReader();
        if (pr == null) throw new IOException("Image has no PixelReader");

        // Raw IDAT body: each scanline = 1 filter byte (0 = None) + RGBA pixels.
        byte[] raw = new byte[h * (1 + w * 4)];
        int pos = 0;
        for (int y = 0; y < h; y++) {
            raw[pos++] = 0;
            for (int x = 0; x < w; x++) {
                int argb = pr.getArgb(x, y);
                raw[pos++] = (byte) ((argb >>> 16) & 0xff);
                raw[pos++] = (byte) ((argb >>> 8)  & 0xff);
                raw[pos++] = (byte) (argb          & 0xff);
                raw[pos++] = (byte) ((argb >>> 24) & 0xff);
            }
        }

        byte[] idat = deflate(raw);

        ByteArrayOutputStream out = new ByteArrayOutputStream(idat.length + 64);
        DataOutputStream dout = new DataOutputStream(out);
        // PNG signature
        dout.write(new byte[] {
                (byte) 0x89, 'P', 'N', 'G',
                (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
        });

        // IHDR chunk
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream(13);
        DataOutputStream ihdrOut = new DataOutputStream(ihdr);
        ihdrOut.writeInt(w);
        ihdrOut.writeInt(h);
        ihdrOut.writeByte(8);   // bit depth per channel
        ihdrOut.writeByte(6);   // color type 6 = truecolor + alpha (RGBA)
        ihdrOut.writeByte(0);   // compression: deflate
        ihdrOut.writeByte(0);   // filter method: adaptive
        ihdrOut.writeByte(0);   // interlace: none
        writeChunk(dout, "IHDR", ihdr.toByteArray());

        writeChunk(dout, "IDAT", idat);
        writeChunk(dout, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] raw) {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            def.setInput(raw);
            def.finish();
            byte[] buf = new byte[Math.max(4096, raw.length / 4 + 1024)];
            ByteArrayOutputStream out = new ByteArrayOutputStream(buf.length);
            while (!def.finished()) {
                int n = def.deflate(buf);
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            def.end();
        }
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        out.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }
}
