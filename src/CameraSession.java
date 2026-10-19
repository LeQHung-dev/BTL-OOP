import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Chụp ảnh bằng camera của máy thông qua trình duyệt (Chrome, Edge, Firefox...), không cần thư viện ngoài.
 *
 * Cách hoạt động: chương trình mở một máy chủ web nhỏ chỉ lắng nghe trên chính máy này (127.0.0.1),
 * mở một trang web có xem trước camera và nút "Chụp ảnh". Khi người dùng bấm "Dùng ảnh này", trang web
 * gửi ảnh JPEG về máy chủ, chương trình nhận ảnh và đóng máy chủ lại.
 */
public class CameraSession {
    private static final int MAX_UPLOAD_BYTES = 25 * 1024 * 1024;

    private final String token = newToken();
    private final CompletableFuture<BufferedImage> result = new CompletableFuture<>();
    private HttpServer server;
    private String url;

    /** Mở máy chủ và trả về địa chỉ trang chụp ảnh. */
    public String start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/" + token + "/";
        return url;
    }

    public String getUrl() { return url; }

    /** Chờ ảnh từ trình duyệt gửi về (tối đa [seconds] giây). */
    public BufferedImage waitForImage(long seconds) throws IOException {
        try {
            return result.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("Hết thời gian chờ, chưa nhận được ảnh từ camera");
        } catch (CancellationException e) {
            throw new IOException("Đã hủy chụp ảnh");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Bị ngắt");
        } catch (ExecutionException e) {
            throw new IOException(e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
    }

    /** Đóng máy chủ (gọi sau khi nhận ảnh hoặc khi người dùng hủy). */
    public void close() {
        result.cancel(true);
        if (server != null) { server.stop(0); server = null; }
    }

    // ---------- Xử lý yêu cầu từ trình duyệt ----------

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String base = "/" + token + "/";
            if (!path.startsWith(base)) { reply(ex, 404, "text/plain; charset=utf-8", "Không tìm thấy"); return; }
            String rest = path.substring(base.length());
            if ("GET".equals(ex.getRequestMethod()) && rest.isEmpty()) {
                reply(ex, 200, "text/html; charset=utf-8", PAGE);
            } else if ("POST".equals(ex.getRequestMethod()) && rest.equals("upload")) {
                byte[] data = readLimited(ex.getRequestBody());
                BufferedImage img = data == null ? null : ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) { reply(ex, 400, "text/plain; charset=utf-8", "Ảnh không hợp lệ"); return; }
                reply(ex, 200, "text/plain; charset=utf-8", "ok");
                result.complete(img);
            } else {
                reply(ex, 404, "text/plain; charset=utf-8", "Không tìm thấy");
            }
        } finally {
            ex.close();
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n, total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_UPLOAD_BYTES) return null;
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static void reply(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static String newToken() {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    // ---------- Trang web chụp ảnh ----------

    private static final String PAGE = String.join("\n",
        "<!DOCTYPE html><html lang='vi'><head><meta charset='utf-8'>",
        "<meta name='viewport' content='width=device-width, initial-scale=1'>",
        "<title>Chụp đơn thuốc</title>",
        "<style>",
        "body{margin:0;font:16px/1.5 'Segoe UI',system-ui,sans-serif;background:#16231d;color:#fff;text-align:center}",
        "header{padding:14px;background:#0e5a45;font-weight:700;font-size:20px}",
        "main{max-width:900px;margin:0 auto;padding:14px}",
        "video,canvas{width:100%;max-height:68vh;background:#000;border-radius:10px;object-fit:contain}",
        "button,select{font:inherit;padding:11px 22px;margin:8px 5px;border-radius:9px;border:2px solid #fff;cursor:pointer}",
        "button{background:#fff;color:#0e5a45;font-weight:700}",
        "button.main{background:#2fbf8f;color:#08281e;border-color:#2fbf8f}",
        "#msg{min-height:28px;margin-top:6px}.err{color:#ffb4ab}.hide{display:none}",
        "</style></head><body>",
        "<header>Chụp đơn thuốc cho Hệ thống bán thuốc - Nhóm 09</header>",
        "<main>",
        "<video id='v' autoplay playsinline muted></video><canvas id='c' class='hide'></canvas>",
        "<div id='msg'>Đang mở camera... hãy chọn \"Cho phép\" nếu trình duyệt hỏi.</div>",
        "<div id='live'><select id='cams' class='hide'></select>",
        "<button class='main' id='snap'>Chụp ảnh</button></div>",
        "<div id='review' class='hide'><button id='retake'>Chụp lại</button>",
        "<button class='main' id='send'>Dùng ảnh này</button></div>",
        "<p style='opacity:.75'>Đặt đơn thuốc thẳng, đủ sáng, chữ rõ nét và lấp đầy khung hình.</p>",
        "</main>",
        "<script>",
        "var v=document.getElementById('v'),c=document.getElementById('c'),msg=document.getElementById('msg');",
        "var cams=document.getElementById('cams'),stream=null,blob=null;",
        "function say(t,e){msg.textContent=t;msg.className=e?'err':'';}",
        "function mode(m){var r=(m==='review');",
        "  v.classList.toggle('hide',r);c.classList.toggle('hide',!r);",
        "  document.getElementById('live').classList.toggle('hide',r);",
        "  document.getElementById('review').classList.toggle('hide',!r);}",
        "function stop(){if(stream){stream.getTracks().forEach(function(t){t.stop();});stream=null;}}",
        "function start(id){",
        "  if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){",
        "    say('Trình duyệt này không hỗ trợ camera. Hãy dùng Chrome hoặc Edge.',true);return;}",
        "  stop();",
        "  var cons={audio:false,video:id?{deviceId:{exact:id}}:{facingMode:'environment',width:{ideal:1920},height:{ideal:1080}}};",
        "  navigator.mediaDevices.getUserMedia(cons).then(function(s){",
        "    stream=s;v.srcObject=s;mode('live');say('Camera sẵn sàng. Bấm \"Chụp ảnh\".');",
        "    return navigator.mediaDevices.enumerateDevices();",
        "  }).then(function(list){",
        "    var vids=list.filter(function(d){return d.kind==='videoinput';});",
        "    if(vids.length>1){cams.innerHTML='';vids.forEach(function(d,i){var o=document.createElement('option');",
        "      o.value=d.deviceId;o.textContent=d.label||('Camera '+(i+1));cams.appendChild(o);});",
        "      cams.classList.remove('hide');}",
        "  }).catch(function(e){say('Không mở được camera ('+(e.name||e)+'). Hãy cho phép trình duyệt dùng camera rồi tải lại trang (F5).',true);});",
        "}",
        "cams.onchange=function(){start(cams.value);};",
        "document.getElementById('snap').onclick=function(){",
        "  if(!v.videoWidth){say('Camera chưa sẵn sàng, đợi một chút rồi bấm lại.',true);return;}",
        "  c.width=v.videoWidth;c.height=v.videoHeight;c.getContext('2d').drawImage(v,0,0);",
        "  c.toBlob(function(b){blob=b;mode('review');say('Xem lại ảnh: chữ có rõ không?');},'image/jpeg',0.92);};",
        "document.getElementById('retake').onclick=function(){mode('live');say('Camera sẵn sàng. Bấm \"Chụp ảnh\".');};",
        "document.getElementById('send').onclick=function(){",
        "  say('Đang gửi ảnh về chương trình...');",
        "  fetch('upload',{method:'POST',headers:{'Content-Type':'image/jpeg'},body:blob}).then(function(r){",
        "    if(!r.ok)throw new Error('Máy chủ từ chối ảnh');",
        "    stop();document.getElementById('review').classList.add('hide');",
        "    say('Đã gửi ảnh về chương trình. Bạn có thể đóng tab này.');",
        "  }).catch(function(e){say('Gửi ảnh thất bại: '+e.message+'. Chương trình có thể đã bị hủy chụp.',true);});};",
        "start();",
        "</script></body></html>");
}
