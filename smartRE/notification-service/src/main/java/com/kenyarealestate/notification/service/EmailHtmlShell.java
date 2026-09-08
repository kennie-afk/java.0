package com.kenyarealestate.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Wraps a rendered template fragment in the SmartRE email shell.
 *
 * <p>Everything here is shaped by what mail clients actually do rather than by what HTML
 * can do:
 *
 * <ul>
 *   <li><b>Tables, not flexbox.</b> Outlook on Windows renders through Word's engine. It
 *       has no flexbox, no grid, and no reliable float. A centred table is the only
 *       layout that behaves everywhere.</li>
 *   <li><b>Inline-ish styles in a &lt;style&gt; block plus attributes.</b> Gmail strips
 *       &lt;style&gt; in some contexts, so anything structural (width, alignment,
 *       background) is also set as an attribute or inline style that survives stripping.
 *       The stylesheet only carries refinements that can be lost without harm.</li>
 *   <li><b>The logo is a CID attachment, not a hosted URL.</b> Most clients block remote
 *       images by default, which would show a broken box on first open; an attached
 *       image renders immediately. It also means a mail read in five years does not
 *       depend on a URL still resolving, and it leaks no open-tracking signal.</li>
 *   <li><b>The wordmark is text, not part of the image.</b> When images are blocked
 *       entirely — still common in corporate mail — the brand and the sender are still
 *       legible. An image-only header would leave a blank rectangle.</li>
 * </ul>
 *
 * <p>The dark-mode block is best-effort. Apple Mail and recent Outlook honour it; Gmail
 * inverts colours on its own terms regardless. The palette is chosen so that an
 * uninvited inversion still leaves gold on a dark ground, which is legible.
 */
@Component
public class EmailHtmlShell {

    /** Referenced from the markup as cid:LOGO_CID; EmailChannel attaches it under this id. */
    public static final String LOGO_CID = "smartre-logo";

    private final String appUrl;

    public EmailHtmlShell(@Value("${app.public-url:https://smartre.co.ke}") String appUrl) {
        this.appUrl = appUrl;
    }

    public String wrap(String fragment) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="color-scheme" content="light dark">
              <title>SmartRE</title>
              <style>
                body { margin:0; padding:0; background:#F6F5F1; }
                .wrap { width:100%%; background:#F6F5F1; padding:24px 12px; }
                .card { width:100%%; max-width:560px; margin:0 auto; background:#FFFFFF;
                        border:1px solid #E7E2D4; border-radius:12px; overflow:hidden; }
                .inner { padding:28px 30px 30px; font-family:Georgia,'Times New Roman',serif;
                         font-size:15px; line-height:1.6; color:#22201B; }
                .inner p { margin:0 0 14px; }
                .brand { font-size:19px; font-weight:700; color:#22201B; letter-spacing:-0.2px; }
                .brand .re { color:#C9A227; }
                .facts { width:100%%; border-collapse:collapse; margin:18px 0; }
                .facts td { padding:7px 0; border-bottom:1px solid #EFEBE0; font-size:14px; }
                .facts .k { color:#7A7263; width:45%%; }
                .facts .v { color:#22201B; font-weight:700; text-align:right; }
                .facts .alert { color:#B4231F; }
                .quote { border-left:3px solid #C9A227; padding-left:12px; color:#4A443A;
                         font-style:italic; }
                .plainlink { word-break:break-all; font-family:monospace; font-size:13px;
                             color:#8C6B1A; }
                .btn { display:inline-block; background:#C9A227; color:#FFFFFF !important;
                       text-decoration:none; font-family:Helvetica,Arial,sans-serif;
                       font-size:14px; font-weight:700; padding:11px 22px; border-radius:8px; }
                .foot { padding:16px 30px 22px; font-family:Helvetica,Arial,sans-serif;
                        font-size:11px; line-height:1.6; color:#8B8371; }
                .foot a { color:#8B8371; }
                @media (prefers-color-scheme: dark) {
                  body, .wrap { background:#14130F !important; }
                  .card { background:#1D1B16 !important; border-color:#312D24 !important; }
                  .inner, .brand, .facts .v { color:#EDE9DE !important; }
                  .facts td { border-bottom-color:#312D24 !important; }
                  .facts .k, .foot { color:#9A9484 !important; }
                }
              </style>
            </head>
            <body>
              <table class="wrap" role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                     border="0" bgcolor="#F6F5F1">
                <tr><td align="center">
                  <table class="card" role="presentation" width="560" cellpadding="0" cellspacing="0"
                         border="0" bgcolor="#FFFFFF">

                    <tr><td style="padding:24px 30px 0;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                        <tr>
                          <td width="36" style="padding-right:10px;" valign="middle">
                            <img src="cid:%s" width="36" height="36" alt="SmartRE"
                                 style="display:block;border:0;">
                          </td>
                          <td valign="middle" class="brand"
                              style="font-family:Georgia,'Times New Roman',serif;font-size:19px;font-weight:700;">
                            Smart<span class="re" style="color:#C9A227;">RE</span>
                          </td>
                        </tr>
                      </table>
                    </td></tr>

                    <tr><td class="inner">
            %s
                    </td></tr>

                    <tr><td class="foot">
                      You are receiving this because of activity on your SmartRE account.
                      <br>
                      <a href="%s">smartre.co.ke</a> &middot; &copy; %d SmartRE
                    </td></tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(LOGO_CID, fragment, appUrl, Year.now().getValue());
    }
}
