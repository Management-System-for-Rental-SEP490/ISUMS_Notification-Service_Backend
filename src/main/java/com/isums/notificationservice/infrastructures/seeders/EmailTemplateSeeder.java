package com.isums.notificationservice.infrastructures.seeders;

import com.isums.notificationservice.domains.entities.EmailTemplate;
import com.isums.notificationservice.domains.entities.EmailTemplateVersion;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.TemplateStatus;
import com.isums.notificationservice.infrastructures.repositories.EmailTemplateRepository;
import com.isums.notificationservice.infrastructures.repositories.EmailTemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class EmailTemplateSeeder {

    @Value("${app.seed.email-templates:true}")
    private boolean enabled;

    @Bean
    @Transactional
    ApplicationRunner seedEmailTemplatesRunner(EmailTemplateRepository templateRepo,
                                               EmailTemplateVersionRepository versionRepo) {
        return args -> {
            if (!enabled) return;
            seed(templateRepo, versionRepo);
        };
    }

    // WELCOME USER (vi_VN)
    @Transactional
    public void seed(EmailTemplateRepository templateRepo, EmailTemplateVersionRepository versionRepo) {

        upsertActiveV1(
                templateRepo, versionRepo,
                "welcome", "ONBOARDING", "CUSTOMER",
                LocaleType.vi_VN,
                "Chao mung {{name}} den voi ISUMS",
                """
                        <!doctype html>
                        <html>
                          <body style="font-family:Arial,sans-serif;">
                            <h2>Chao mung {{name}}!</h2>
                            <p>Tai khoan cua ban da duoc kich hoat.</p>
                            <p>Bat dau tai: <a href="{{appUrl}}">{{appUrl}}</a></p>
                            <p style="color:#888;font-size:12px;">Ho tro: {{supportEmail}}</p>
                          </body>
                        </html>
                        """,
                """
                        Chao mung {{name}}!
                        Bat dau tai: {{appUrl}}
                        Ho tro: {{supportEmail}}
                        """,
                List.of("name", "appUrl", "supportEmail"),
                "system"
        );

        // E-CONTRACT VIEW + CONFIRM (vi_VN)
        upsertActiveV1(
                templateRepo, versionRepo,
                "econtract_view_confirm",
                "CONTRACT",
                "TENANT",
                LocaleType.vi_VN,
                "Vui lòng xem và xác nhận hợp đồng {{contractNo}}",
                """
                        <!doctype html>
                        <html lang="vi">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width,initial-scale=1">
                          <title>Xác nhận hợp đồng thuê nhà</title>
                        </head>
                        <body style="margin:0;padding:0;background:#f6f7fb;">
                          <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                            Bạn có một hợp đồng thuê nhà cần xem và xác nhận.
                          </div>
                        
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f6f7fb;padding:24px 12px;">
                            <tr>
                              <td align="center">
                                <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                                       style="width:100%;max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 6px 18px rgba(18,38,63,.08);">
                        
                                  <tr>
                                    <td style="padding:20px 24px;background:#0b5cff;color:#ffffff;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:18px;font-weight:700;line-height:1.2;">
                                        ISUMS • Hợp đồng thuê nhà
                                      </div>
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;opacity:.9;margin-top:6px;">
                                        Vui lòng xem và xác nhận hợp đồng
                                      </div>
                                    </td>
                                  </tr>
                        
                                  <tr>
                                    <td style="padding:24px;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:16px;color:#101828;line-height:1.6;">
                                        Xin chào <strong>{{tenantName}}</strong>,
                                      </div>
                        
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#344054;line-height:1.7;margin-top:10px;">
                                        Bạn vừa nhận được hợp đồng thuê nhà từ <strong>{{landlordName}}</strong>.
                                        Vui lòng nhấn <strong>Xem hợp đồng</strong> để đọc nội dung và tiến hành xác nhận nếu đồng ý.
                                      </div>
                        
                                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin-top:16px;">
                                        <tr>
                                          <td style="padding:14px 16px;background:#f2f4f7;border-radius:12px;">
                                            <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#475467;line-height:1.6;">
                                              <div><strong>Mã hợp đồng:</strong> {{contractNo}}</div>
                                              <div><strong>Tên hợp đồng:</strong> {{contractName}}</div>
                                              <div><strong>Địa chỉ:</strong> {{propertyAddress}}</div>
                                              <div><strong>Thời hạn:</strong> {{startDate}} – {{endDate}}</div>
                                            </div>
                                          </td>
                                        </tr>
                                      </table>
                        
                                      <table role="presentation" cellpadding="0" cellspacing="0" style="margin-top:20px;">
                                        <tr>
                                          <td>
                                            <a href="{{viewUrl}}"
                                               style="display:inline-block;background:#0b5cff;color:#ffffff;text-decoration:none;font-family:Arial,Helvetica,sans-serif;
                                                      font-size:14px;font-weight:700;padding:12px 18px;border-radius:10px;">
                                              Xem hợp đồng
                                            </a>
                                          </td>
                                          <td style="width:12px;"></td>
                                          <td>
                                            <a href="{{confirmUrl}}"
                                               style="display:inline-block;background:#12b76a;color:#ffffff;text-decoration:none;font-family:Arial,Helvetica,sans-serif;
                                                      font-size:14px;font-weight:700;padding:12px 18px;border-radius:10px;">
                                              Tôi đồng ý (Xác nhận)
                                            </a>
                                          </td>
                                        </tr>
                                      </table>
                        
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#667085;line-height:1.6;margin-top:18px;">
                                        Nếu bạn không bấm được nút, hãy copy &amp; paste link sau vào trình duyệt:
                                        <div style="word-break:break-all;margin-top:6px;">
                                          <strong>Xem hợp đồng:</strong> {{viewUrl}}<br>
                                          <strong>Xác nhận:</strong> {{confirmUrl}}
                                        </div>
                                      </div>
                        
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#667085;line-height:1.6;margin-top:14px;">
                                        Vì lý do bảo mật, đường dẫn xác nhận có thể hết hạn sau <strong>{{expiresIn}}</strong>.
                                        Nếu bạn không yêu cầu email này, vui lòng bỏ qua.
                                      </div>
                        
                                      <hr style="border:none;border-top:1px solid #eaecf0;margin:22px 0;">
                        
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#98a2b3;line-height:1.6;">
                                        Trân trọng,<br>
                                        Đội ngũ ISUMS
                                      </div>
                                    </td>
                                  </tr>
                        
                                  <tr>
                                    <td style="padding:16px 24px;background:#fafafa;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#98a2b3;line-height:1.6;">
                                        Email này được gửi tự động. Vui lòng không trả lời trực tiếp email này.
                                      </div>
                                    </td>
                                  </tr>
                        
                                </table>
                              </td>
                            </tr>
                          </table>
                        </body>
                        </html>
                        """,
                """
                        Xin chào {{tenantName}}
                        
                        Bạn vừa nhận được hợp đồng thuê nhà từ {{landlordName}}.
                        
                        Mã hợp đồng: {{contractNo}}
                        Tên hợp đồng: {{contractName}}
                        Địa chỉ: {{propertyAddress}}
                        Thời hạn: {{startDate}} - {{endDate}}
                        
                        Xem hợp đồng: {{viewUrl}}
                        Xác nhận đồng ý: {{confirmUrl}}
                        
                        Lưu ý: Link xác nhận có thể hết hạn sau {{expiresIn}}.
                        """,
                List.of(
                        "tenantName",
                        "landlordName",
                        "contractNo",
                        "contractName",
                        "propertyAddress",
                        "startDate",
                        "endDate",
                        "viewUrl",
                        "confirmUrl",
                        "expiresIn"
                ),
                "system"
        );

        // USER ACTIVATED (vi_VN)
        upsertActiveV1(
                templateRepo, versionRepo,
                "user_activated",
                "ONBOARDING",
                "CUSTOMER",
                LocaleType.vi_VN,
                "Tài khoản của bạn đã được kích hoạt",
                """
                        <!doctype html>
                        <html lang="vi">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width,initial-scale=1">
                          <title>Tài khoản đã được kích hoạt</title>
                        </head>
                        <body style="margin:0;padding:0;background:#f6f7fb;">
                          <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                            Tài khoản ISUMS của bạn đã sẵn sàng. Đây là thông tin đăng nhập tạm thời.
                          </div>
                        
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                                 style="background:#f6f7fb;padding:24px 12px;">
                            <tr>
                              <td align="center">
                                <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                                       style="width:100%;max-width:600px;background:#ffffff;border-radius:16px;
                                              overflow:hidden;box-shadow:0 6px 18px rgba(18,38,63,.08);">
                        
                                  <!-- Header -->
                                  <tr>
                                    <td style="padding:20px 24px;background:#101828;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:18px;
                                                  font-weight:700;color:#ffffff;line-height:1.2;">
                                        ISUMS
                                      </div>
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;
                                                  color:#98a2b3;margin-top:4px;">
                                        Hệ thống quản lý thuê trọ
                                      </div>
                                    </td>
                                  </tr>
                        
                                  <!-- Status Badge -->
                                  <tr>
                                    <td style="padding:28px 24px 0;">
                                      <div style="display:inline-block;background:#ecfdf3;border:1px solid #abefc6;
                                                  border-radius:20px;padding:5px 14px;">
                                        <span style="font-family:Arial,Helvetica,sans-serif;font-size:12px;
                                                     font-weight:700;color:#067647;letter-spacing:.5px;">
                                          ✓ &nbsp;TÀI KHOẢN ĐÃ KÍCH HOẠT
                                        </span>
                                      </div>
                                    </td>
                                  </tr>
                        
                                  <!-- Body -->
                                  <tr>
                                    <td style="padding:16px 24px 24px;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:22px;
                                                  font-weight:700;color:#101828;line-height:1.3;margin-bottom:10px;">
                                        Xin chào, {{name}}!
                                      </div>
                        
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:14px;
                                                  color:#475467;line-height:1.75;">
                                        Tài khoản của bạn trên hệ thống <strong>ISUMS</strong> đã được tạo và kích hoạt thành công.
                                        Dưới đây là thông tin đăng nhập tạm thời — vui lòng đổi mật khẩu ngay sau lần đăng nhập đầu tiên.
                                      </div>
                        
                                      <!-- Credentials Box -->
                                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                                             style="margin-top:20px;border:1px solid #eaecf0;border-radius:12px;overflow:hidden;">
                                        <tr>
                                          <td style="padding:14px 16px;background:#f9fafb;border-bottom:1px solid #eaecf0;">
                                            <div style="font-family:Arial,Helvetica,sans-serif;font-size:11px;
                                                        font-weight:700;color:#98a2b3;letter-spacing:.8px;text-transform:uppercase;">
                                              Thông tin đăng nhập
                                            </div>
                                          </td>
                                        </tr>
                                        <tr>
                                          <td style="padding:16px;">
                                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                                              <tr>
                                                <td style="padding:6px 0;width:120px;">
                                                  <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#98a2b3;">
                                                    Email
                                                  </div>
                                                </td>
                                                <td style="padding:6px 0;">
                                                  <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;
                                                              font-weight:600;color:#101828;">
                                                    {{email}}
                                                  </div>
                                                </td>
                                              </tr>
                                              <tr>
                                                <td colspan="2">
                                                  <div style="border-top:1px solid #f2f4f7;margin:4px 0;"></div>
                                                </td>
                                              </tr>
                                              <tr>
                                                <td style="padding:6px 0;width:120px;">
                                                  <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#98a2b3;">
                                                    Mật khẩu tạm
                                                  </div>
                                                </td>
                                                <td style="padding:6px 0;">
                                                  <div style="display:inline-block;font-family:'Courier New',monospace;
                                                              font-size:14px;font-weight:700;color:#101828;
                                                              background:#f2f4f7;border-radius:6px;padding:3px 10px;
                                                              letter-spacing:1.5px;">
                                                    {{password}}
                                                  </div>
                                                </td>
                                              </tr>
                                            </table>
                                          </td>
                                        </tr>
                                      </table>
                        
                                      <!-- Warning Note -->
                                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                                             style="margin-top:14px;">
                                        <tr>
                                          <td style="padding:12px 14px;background:#fffaeb;border:1px solid #fedf89;
                                                     border-radius:10px;">
                                            <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;
                                                        color:#b54708;line-height:1.6;">
                                              ⚠️ &nbsp;Mật khẩu này chỉ dùng một lần. Vui lòng đổi mật khẩu ngay sau khi đăng nhập để bảo mật tài khoản.
                                            </div>
                                          </td>
                                        </tr>
                                      </table>
                        
                                      <hr style="border:none;border-top:1px solid #eaecf0;margin:24px 0 16px;">
                        
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;
                                                  color:#98a2b3;line-height:1.7;">
                                        Trân trọng,<br>
                                        <strong style="color:#475467;">Đội ngũ ISUMS</strong>
                                      </div>
                                    </td>
                                  </tr>
                        
                                  <!-- Footer -->
                                  <tr>
                                    <td style="padding:14px 24px;background:#f9fafb;border-top:1px solid #eaecf0;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:11px;
                                                  color:#98a2b3;line-height:1.6;">
                                        Email này được gửi tự động. Vui lòng không trả lời trực tiếp.
                                      </div>
                                    </td>
                                  </tr>
                        
                                </table>
                              </td>
                            </tr>
                          </table>
                        </body>
                        </html>
                        """,
                """
                        Xin chào {{name}},
                        
                        Tài khoản ISUMS của bạn đã được kích hoạt thành công.
                        
                        Thông tin đăng nhập:
                        - Email    : {{email}}
                        - Mật khẩu: {{password}}
                        
                        Lưu ý: Đây là mật khẩu tạm thời, vui lòng đổi mật khẩu ngay sau khi đăng nhập.
                        
                        Trân trọng,
                        Đội ngũ ISUMS
                        """,
                List.of("name", "email", "password"),
                "system"
        );

        upsertActiveV1(
                templateRepo, versionRepo,
                "payment_invoice",
                "PAYMENT",
                "TENANT",
                LocaleType.vi_VN,
                "Hóa đơn {{invoiceType}} cần thanh toán trước {{dueDate}}",
                """
                        <!doctype html>
                        <html lang="vi">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width,initial-scale=1">
                          <title>Hóa đơn thanh toán</title>
                        </head>
                        <body style="margin:0;padding:0;background:#f6f7fb;">
                          <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                            Bạn có hóa đơn {{invoiceType}} cần thanh toán trước {{dueDate}}.
                          </div>
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                            <tr>
                              <td align="center" style="padding:32px 16px;">
                                <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                                       style="background:#fff;border-radius:16px;overflow:hidden;
                                              box-shadow:0 2px 8px rgba(0,0,0,.08);max-width:600px;width:100%;">
                        
                                  <tr>
                                    <td style="background:#1a56db;padding:28px 32px;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:22px;
                                                  font-weight:700;color:#fff;">💳 Hóa đơn thanh toán</div>
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;
                                                  color:#bfdbfe;margin-top:6px;">ISUMS — Hệ thống quản lý nhà trọ</div>
                                    </td>
                                  </tr>
                        
                                  <tr>
                                    <td style="padding:28px 32px;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:15px;
                                                  color:#374151;line-height:1.7;">
                                        Xin chào,<br>
                                        Bạn có một hóa đơn cần thanh toán. Vui lòng thanh toán trước hạn để tránh phát sinh phí trễ hạn.
                                      </div>
                        
                                      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                                             style="margin-top:20px;background:#f9fafb;border-radius:12px;overflow:hidden;">
                                        <tr>
                                          <td style="padding:16px 20px;border-bottom:1px solid #e5e7eb;">
                                            <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;
                                                        color:#6b7280;text-transform:uppercase;letter-spacing:.5px;">
                                              Loại hóa đơn</div>
                                            <div style="font-family:Arial,Helvetica,sans-serif;font-size:16px;
                                                        font-weight:700;color:#111827;margin-top:4px;">{{invoiceType}}</div>
                                          </td>
                                        </tr>
                                        <tr>
                                          <td style="padding:16px 20px;border-bottom:1px solid #e5e7eb;">
                                            <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;
                                                        color:#6b7280;text-transform:uppercase;letter-spacing:.5px;">
                                              Số tiền</div>
                                            <div style="font-family:Arial,Helvetica,sans-serif;font-size:22px;
                                                        font-weight:700;color:#1a56db;margin-top:4px;">{{amount}}</div>
                                          </td>
                                        </tr>
                                        <tr>
                                          <td style="padding:16px 20px;">
                                            <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;
                                                        color:#6b7280;text-transform:uppercase;letter-spacing:.5px;">
                                              Hạn thanh toán</div>
                                            <div style="font-family:Arial,Helvetica,sans-serif;font-size:16px;
                                                        font-weight:700;color:#dc2626;margin-top:4px;">{{dueDate}}</div>
                                          </td>
                                        </tr>
                                      </table>
                        
                                      <table role="presentation" cellpadding="0" cellspacing="0" style="margin-top:24px;">
                                        <tr>
                                          <td>
                                            <a href="{{paymentUrl}}"
                                               style="display:inline-block;background:#1a56db;color:#fff;
                                                      text-decoration:none;font-family:Arial,Helvetica,sans-serif;
                                                      font-size:15px;font-weight:700;padding:14px 28px;
                                                      border-radius:10px;letter-spacing:.3px;">
                                              Thanh toán ngay
                                            </a>
                                          </td>
                                        </tr>
                                      </table>
                        
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;
                                                  color:#6b7280;line-height:1.6;margin-top:18px;">
                                        Nếu không bấm được nút, hãy copy link sau vào trình duyệt:<br>
                                        <div style="word-break:break-all;margin-top:4px;color:#1a56db;">{{paymentUrl}}</div>
                                      </div>
                        
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;
                                                  color:#6b7280;line-height:1.6;margin-top:14px;">
                                        Link thanh toán hợp lệ trong <strong>{{expiresIn}}</strong>.
                                        Nếu bạn không yêu cầu email này, vui lòng bỏ qua.
                                      </div>
                        
                                      <hr style="border:none;border-top:1px solid #e5e7eb;margin:22px 0;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#9ca3af;">
                                        Trân trọng,<br>Đội ngũ ISUMS
                                      </div>
                                    </td>
                                  </tr>
                        
                                  <tr>
                                    <td style="padding:14px 32px;background:#f9fafb;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#9ca3af;">
                                        Email này được gửi tự động. Vui lòng không trả lời trực tiếp.
                                      </div>
                                    </td>
                                  </tr>
                        
                                </table>
                              </td>
                            </tr>
                          </table>
                        </body>
                        </html>
                        """,
                // ── TEXT ──────────────────────────────────────────────────────
                """
                        Xin chào,
                        
                        Bạn có hóa đơn {{invoiceType}} cần thanh toán.
                        
                        Số tiền: {{amount}}
                        Hạn nộp: {{dueDate}}
                        
                        Thanh toán tại: {{paymentUrl}}
                        
                        Link có hiệu lực trong {{expiresIn}}.
                        
                        Trân trọng,
                        Đội ngũ ISUMS
                        """,
                List.of("invoiceType", "amount", "dueDate", "paymentUrl", "expiresIn"),
                "system"
        );

        upsertActiveV1(
                templateRepo, versionRepo,
                "payment_receipt",
                "PAYMENT",
                "TENANT",
                LocaleType.vi_VN,
                "Xác nhận thanh toán {{invoiceType}} thành công",
                """
                <!doctype html>
                <html lang="vi">
                <head><meta charset="utf-8"><title>Xác nhận thanh toán</title></head>
                <body style="margin:0;padding:0;background:#f6f7fb;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:32px 16px;">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                             style="background:#fff;border-radius:16px;overflow:hidden;
                                    box-shadow:0 2px 8px rgba(0,0,0,.08);max-width:600px;width:100%;">
         
                        <tr>
                          <td style="background:#16a34a;padding:28px 32px;">
                            <div style="font-family:Arial,sans-serif;font-size:22px;font-weight:700;color:#fff;">
                              ✅ Thanh toán thành công
                            </div>
                            <div style="font-family:Arial,sans-serif;font-size:13px;color:#bbf7d0;margin-top:6px;">
                              ISUMS — Hệ thống quản lý nhà trọ
                            </div>
                          </td>
                        </tr>
         
                        <tr>
                          <td style="padding:28px 32px;">
                            <div style="font-family:Arial,sans-serif;font-size:15px;color:#374151;line-height:1.7;">
                              Xin chào <strong>{{tenantName}}</strong>,<br>
                              Hệ thống đã ghi nhận thanh toán của bạn.
                            </div>
         
                            <table role="presentation" width="100%" style="margin-top:20px;border-radius:12px;
                                    overflow:hidden;background:#f9fafb;">
                              <tr>
                                <td style="padding:14px 20px;border-bottom:1px solid #e5e7eb;">
                                  <div style="font-size:12px;color:#6b7280;font-family:Arial,sans-serif;">Loại thanh toán</div>
                                  <div style="font-size:16px;font-weight:700;color:#111827;font-family:Arial,sans-serif;margin-top:4px;">
                                    {{invoiceType}}
                                  </div>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:14px 20px;border-bottom:1px solid #e5e7eb;">
                                  <div style="font-size:12px;color:#6b7280;font-family:Arial,sans-serif;">Số tiền</div>
                                  <div style="font-size:22px;font-weight:700;color:#16a34a;font-family:Arial,sans-serif;margin-top:4px;">
                                    {{amount}}
                                  </div>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:14px 20px;border-bottom:1px solid #e5e7eb;">
                                  <div style="font-size:12px;color:#6b7280;font-family:Arial,sans-serif;">Mã giao dịch</div>
                                  <div style="font-size:14px;font-weight:600;color:#374151;font-family:Arial,sans-serif;margin-top:4px;">
                                    {{txnNo}}
                                  </div>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:14px 20px;">
                                  <div style="font-size:12px;color:#6b7280;font-family:Arial,sans-serif;">Thời gian</div>
                                  <div style="font-size:14px;font-weight:600;color:#374151;font-family:Arial,sans-serif;margin-top:4px;">
                                    {{paidAt}}
                                  </div>
                                </td>
                              </tr>
                            </table>
         
                            <div style="font-family:Arial,sans-serif;font-size:13px;color:#6b7280;margin-top:20px;line-height:1.6;">
                              Vui lòng lưu lại email này như biên nhận thanh toán.
                              Nếu có thắc mắc, liên hệ chủ nhà hoặc hỗ trợ ISUMS.
                            </div>
         
                            <hr style="border:none;border-top:1px solid #e5e7eb;margin:22px 0;">
                            <div style="font-family:Arial,sans-serif;font-size:12px;color:#9ca3af;">
                              Trân trọng,<br>Đội ngũ ISUMS
                            </div>
                          </td>
                        </tr>
         
                        <tr>
                          <td style="padding:14px 32px;background:#f9fafb;">
                            <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">
                              Email này được gửi tự động. Vui lòng không trả lời trực tiếp.
                            </div>
                          </td>
                        </tr>
         
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """,
                """
                Xin chào {{tenantName}},
         
                Hệ thống đã ghi nhận thanh toán:
                - Loại: {{invoiceType}}
                - Số tiền: {{amount}}
                - Mã GD: {{txnNo}}
                - Thời gian: {{paidAt}}
         
                Vui lòng lưu lại email này như biên nhận.
         
                Trân trọng,
                Đội ngũ ISUMS
                """,
                List.of("tenantName", "invoiceType", "amount", "txnNo", "paidAt"),
                "system"
        );

        upsertActiveV1(
                templateRepo, versionRepo,
                "user_activated",
                "ONBOARDING",
                "TENANT",
                LocaleType.vi_VN,
                "Chào mừng {{name}} — Tài khoản đã sẵn sàng",
                """
                <!doctype html>
                <html lang="vi">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Tài khoản đã kích hoạt</title></head>
                <body style="margin:0;padding:0;background:#f0f4f8;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:36px 16px;">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                             style="background:#fff;border-radius:20px;overflow:hidden;
                                    box-shadow:0 4px 24px rgba(0,0,0,.08);max-width:600px;width:100%;">
        
                        <!-- Header -->
                        <tr>
                          <td style="background:linear-gradient(135deg,#1e40af 0%,#3b82f6 100%);padding:36px 40px;">
                            <div style="font-family:Arial,sans-serif;font-size:11px;font-weight:700;
                                        color:#93c5fd;letter-spacing:2px;text-transform:uppercase;margin-bottom:12px;">
                              ISUMS · Quản lý nhà trọ
                            </div>
                            <div style="font-family:Arial,sans-serif;font-size:26px;font-weight:700;color:#fff;line-height:1.3;">
                              Chào mừng bạn! 🎉
                            </div>
                            <div style="font-family:Arial,sans-serif;font-size:14px;color:#bfdbfe;margin-top:8px;">
                              Tài khoản của bạn đã được kích hoạt thành công
                            </div>
                          </td>
                        </tr>
        
                        <!-- Body -->
                        <tr>
                          <td style="padding:36px 40px;">
                            <div style="font-family:Arial,sans-serif;font-size:15px;color:#374151;line-height:1.8;">
                              Xin chào <strong style="color:#1e40af;">{{name}}</strong>,<br>
                              Chủ nhà đã kích hoạt tài khoản ISUMS cho bạn.
                              Dưới đây là thông tin đăng nhập tạm thời — vui lòng đổi mật khẩu ngay sau khi đăng nhập.
                            </div>
        
                            <!-- Thông tin đăng nhập -->
                            <table role="presentation" width="100%"
                                   style="margin-top:24px;border-radius:14px;overflow:hidden;
                                          background:#f8faff;border:1.5px solid #dbeafe;">
                              <tr>
                                <td style="padding:10px 24px;background:#eff6ff;border-bottom:1px solid #dbeafe;">
                                  <div style="font-family:Arial,sans-serif;font-size:11px;font-weight:700;
                                              color:#3b82f6;letter-spacing:1.5px;text-transform:uppercase;">
                                    Thông tin đăng nhập
                                  </div>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:16px 24px;border-bottom:1px solid #dbeafe;">
                                  <div style="font-family:Arial,sans-serif;font-size:12px;color:#6b7280;">Email</div>
                                  <div style="font-family:Arial,sans-serif;font-size:15px;font-weight:600;
                                              color:#111827;margin-top:3px;">{{email}}</div>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:16px 24px;">
                                  <div style="font-family:Arial,sans-serif;font-size:12px;color:#6b7280;">Mật khẩu tạm thời</div>
                                  <div style="font-family:monospace;font-size:20px;font-weight:700;
                                              color:#1e40af;margin-top:6px;letter-spacing:2px;
                                              background:#eff6ff;padding:10px 16px;border-radius:8px;
                                              display:inline-block;">{{password}}</div>
                                </td>
                              </tr>
                            </table>
        
                            <!-- Hóa đơn cần thanh toán (chỉ hiện nếu có) -->
                            {{#hasInvoice}}
                            <div style="margin-top:28px;">
                              <div style="font-family:Arial,sans-serif;font-size:13px;font-weight:700;
                                          color:#92400e;letter-spacing:1px;text-transform:uppercase;
                                          margin-bottom:12px;">
                                ⚡ Khoản cần thanh toán ngay
                              </div>
                              <table role="presentation" width="100%"
                                     style="border-radius:14px;overflow:hidden;
                                            background:#fffbeb;border:1.5px solid #fcd34d;">
                                <tr>
                                  <td style="padding:16px 24px;border-bottom:1px solid #fde68a;">
                                    <div style="font-family:Arial,sans-serif;font-size:12px;color:#92400e;">Loại hóa đơn</div>
                                    <div style="font-family:Arial,sans-serif;font-size:15px;font-weight:600;
                                                color:#111827;margin-top:3px;">{{invoiceType}}</div>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:16px 24px;border-bottom:1px solid #fde68a;">
                                    <div style="font-family:Arial,sans-serif;font-size:12px;color:#92400e;">Số tiền</div>
                                    <div style="font-family:Arial,sans-serif;font-size:22px;font-weight:700;
                                                color:#d97706;margin-top:3px;">{{invoiceAmount}}</div>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:16px 24px;border-bottom:1px solid #fde68a;">
                                    <div style="font-family:Arial,sans-serif;font-size:12px;color:#92400e;">Hạn thanh toán</div>
                                    <div style="font-family:Arial,sans-serif;font-size:15px;font-weight:600;
                                                color:#111827;margin-top:3px;">{{invoiceDueDate}}</div>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:20px 24px;text-align:center;">
                                    <a href="{{invoicePaymentUrl}}"
                                       style="display:inline-block;background:#f59e0b;color:#fff;
                                              font-family:Arial,sans-serif;font-size:15px;font-weight:700;
                                              text-decoration:none;padding:14px 36px;border-radius:10px;">
                                      Thanh toán ngay →
                                    </a>
                                  </td>
                                </tr>
                              </table>
                            </div>
                            {{/hasInvoice}}
        
                            <!-- Note -->
                            <div style="margin-top:28px;padding:16px 20px;background:#f9fafb;
                                        border-radius:10px;border-left:3px solid #3b82f6;">
                              <div style="font-family:Arial,sans-serif;font-size:13px;color:#6b7280;line-height:1.7;">
                                💡 Sau khi đăng nhập lần đầu, hệ thống sẽ yêu cầu bạn đổi mật khẩu mới.<br>
                                Mọi hóa đơn và lịch sử thanh toán có thể xem trong ứng dụng ISUMS.
                              </div>
                            </div>
        
                            <hr style="border:none;border-top:1px solid #f3f4f6;margin:28px 0 20px;">
                            <div style="font-family:Arial,sans-serif;font-size:13px;color:#9ca3af;line-height:1.6;">
                              Trân trọng,<br><strong style="color:#374151;">Đội ngũ ISUMS</strong>
                            </div>
                          </td>
                        </tr>
        
                        <!-- Footer -->
                        <tr>
                          <td style="padding:16px 40px;background:#f9fafb;border-top:1px solid #f3f4f6;">
                            <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">
                              Email này được gửi tự động. Vui lòng không trả lời trực tiếp.
                            </div>
                          </td>
                        </tr>
        
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """,
                """
                Xin chào {{name}},
        
                Tài khoản ISUMS của bạn đã được kích hoạt.
        
                Thông tin đăng nhập:
                - Email    : {{email}}
                - Mật khẩu: {{password}}
        
                {{#hasInvoice}}
                Khoản cần thanh toán:
                - Loại    : {{invoiceType}}
                - Số tiền : {{invoiceAmount}}
                - Hạn TT  : {{invoiceDueDate}}
                - Link    : {{invoicePaymentUrl}}
                {{/hasInvoice}}
        
                Vui lòng đổi mật khẩu sau khi đăng nhập lần đầu.
        
                Trân trọng,
                Đội ngũ ISUMS
                """,
                List.of("name", "email", "password", "hasInvoice",
                        "invoiceType", "invoiceAmount", "invoiceDueDate", "invoicePaymentUrl"),
                "system"
        );

        upsertActiveV1(
                templateRepo, versionRepo,
                "contract_completed", "CONTRACT", "TENANT", LocaleType.vi_VN,
                "Hợp đồng đã ký thành công — Tải về tại đây",
                """
                <!doctype html>
                <html lang="vi">
                <head><meta charset="utf-8"></head>
                <body style="margin:0;padding:0;background:#f0f4f8;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:36px 16px;">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                             style="background:#fff;border-radius:20px;overflow:hidden;
                                    box-shadow:0 4px 24px rgba(0,0,0,.08);max-width:600px;width:100%;">
                        <tr>
                          <td style="background:linear-gradient(135deg,#1e40af 0%,#3b82f6 100%);padding:36px 40px;">
                            <div style="font-family:Arial,sans-serif;font-size:11px;font-weight:700;
                                        color:#93c5fd;letter-spacing:2px;text-transform:uppercase;margin-bottom:12px;">
                              ISUMS · Quản lý nhà trọ
                            </div>
                            <div style="font-family:Arial,sans-serif;font-size:26px;font-weight:700;color:#fff;">
                              Hợp đồng đã hoàn tất ✅
                            </div>
                            <div style="font-family:Arial,sans-serif;font-size:14px;color:#bfdbfe;margin-top:8px;">
                              Cả hai bên đã ký điện tử thành công
                            </div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:36px 40px;">
                            <div style="font-family:Arial,sans-serif;font-size:15px;color:#374151;line-height:1.8;">
                              Hợp đồng mã <strong style="color:#1e40af;">{{contractId}}</strong>
                              đã được ký bởi tất cả các bên và có hiệu lực pháp lý.<br>
                              Bạn có thể tải về bản gốc có chữ ký số tại đây:
                            </div>
                            <div style="margin-top:28px;text-align:center;">
                              <a href="{{signedPdfUrl}}"
                                 style="display:inline-block;background:#1e40af;color:#fff;
                                        font-family:Arial,sans-serif;font-size:15px;font-weight:700;
                                        text-decoration:none;padding:16px 40px;border-radius:10px;">
                                📄 Tải hợp đồng có chữ ký số
                              </a>
                            </div>
                            <div style="margin-top:24px;padding:16px 20px;background:#f0fdf4;
                                        border-radius:10px;border-left:3px solid #22c55e;">
                              <div style="font-family:Arial,sans-serif;font-size:13px;color:#166534;line-height:1.7;">
                                ✅ Hợp đồng này có giá trị pháp lý tương đương bản giấy theo quy định.<br>
                                📎 Link tải sẽ hết hạn sau 7 ngày. Vui lòng lưu lại file PDF.
                              </div>
                            </div>
                            <hr style="border:none;border-top:1px solid #f3f4f6;margin:28px 0 20px;">
                            <div style="font-family:Arial,sans-serif;font-size:13px;color:#9ca3af;">
                              Trân trọng,<br><strong style="color:#374151;">Đội ngũ ISUMS</strong>
                            </div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 40px;background:#f9fafb;border-top:1px solid #f3f4f6;">
                            <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">
                              Email này được gửi tự động. Vui lòng không trả lời trực tiếp.
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """,
                """
                Hợp đồng {{contractId}} đã được ký hoàn tất.
                Tải về tại: {{signedPdfUrl}}
                Link hết hạn sau 7 ngày.
                """,
                List.of("contractId", "signedPdfUrl"),
                "system"
        );


        // ── INSPECTION DONE REVIEW (manager) ──────────────────────────────
        upsertActiveV1(
                templateRepo, versionRepo,
                "inspection_done_review", "CONTRACT", "MANAGER",
                LocaleType.vi_VN,
                "Kiểm tra nhà hoàn tất — Hợp đồng #{{contractId}}",
                """
                <!doctype html>
                <html>
                <body style="margin:0;padding:0;background:#f3f4f6;">
                  <table width="100%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:32px 16px;">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;
                                    box-shadow:0 1px 4px rgba(0,0,0,.08);">
                        <tr>
                          <td style="padding:28px 32px;background:#1d4ed8;">
                            <div style="font-family:Arial,sans-serif;font-size:20px;
                                        font-weight:700;color:#ffffff;">
                              ✅ Kiểm tra nhà hoàn tất
                            </div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 32px;">
                            <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                              Kính gửi <strong>{{managerName}}</strong>,
                            </p>
                            <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                              Nhân viên đã hoàn thành kiểm tra nhà cho hợp đồng
                              <strong>#{{contractId}}</strong>.
                            </p>
                            <table width="100%" style="border:1px solid #e5e7eb;
                                   border-radius:8px;border-collapse:collapse;margin:20px 0;">
                              <tr style="background:#f9fafb;">
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;color:#6b7280;width:40%;">
                                  Mã kiểm tra
                                </td>
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;font-weight:600;color:#111827;">
                                  {{inspectionId}}
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;color:#6b7280;
                                           border-top:1px solid #e5e7eb;">
                                  Số tiền khấu trừ đề xuất
                                </td>
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;font-weight:600;color:#dc2626;
                                           border-top:1px solid #e5e7eb;">
                                  {{deductionAmount}}
                                </td>
                              </tr>
                              <tr style="background:#f9fafb;">
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;color:#6b7280;
                                           border-top:1px solid #e5e7eb;">
                                  Ghi chú
                                </td>
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;color:#374151;
                                           border-top:1px solid #e5e7eb;">
                                  {{notes}}
                                </td>
                              </tr>
                            </table>
                            <p style="font-family:Arial,sans-serif;font-size:14px;color:#374151;">
                              Vui lòng đăng nhập hệ thống để xem chi tiết và xác nhận
                              số tiền hoàn cọc cho khách.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:14px 32px;background:#f9fafb;">
                            <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">
                              Email này được gửi tự động. Vui lòng không trả lời trực tiếp.
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """,
                """
                Kính gửi {{managerName}},
                
                Nhân viên đã hoàn thành kiểm tra nhà cho hợp đồng #{{contractId}}.
                
                Mã kiểm tra: {{inspectionId}}
                Số tiền khấu trừ đề xuất: {{deductionAmount}}
                Ghi chú: {{notes}}
                
                Vui lòng đăng nhập hệ thống để xác nhận hoàn cọc.
                """,
                List.of("managerName", "contractId", "inspectionId",
                        "houseId", "deductionAmount", "notes"),
                "system"
        );

// ── CONTRACT EXPIRED INSPECTION SCHEDULED (manager) ───────────────
        upsertActiveV1(
                templateRepo, versionRepo,
                "contract_expired_inspection_scheduled", "CONTRACT", "MANAGER",
                LocaleType.vi_VN,
                "Hợp đồng #{{contractId}} đã hết hạn — Đã lên lịch kiểm tra nhà",
                """
                <!doctype html>
                <html>
                <body style="margin:0;padding:0;background:#f3f4f6;">
                  <table width="100%" cellpadding="0" cellspacing="0">
                    <tr><td align="center" style="padding:32px 16px;">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;
                                    box-shadow:0 1px 4px rgba(0,0,0,.08);">
                        <tr>
                          <td style="padding:28px 32px;background:#92400e;">
                            <div style="font-family:Arial,sans-serif;font-size:20px;
                                        font-weight:700;color:#ffffff;">
                              🔔 Hợp đồng hết hạn — Đã phân công kiểm tra nhà
                            </div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 32px;">
                            <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                              Kính gửi <strong>{{managerName}}</strong>,
                            </p>
                            <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                              Hợp đồng <strong>#{{contractId}}</strong> của khách
                              <strong>{{tenantName}}</strong> đã hết hạn.
                            </p>
                            <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                              Hệ thống đã tự động tạo lịch kiểm tra nhà và phân công
                              nhân viên phụ trách.
                            </p>
                            <table width="100%" style="border:1px solid #e5e7eb;
                                   border-radius:8px;border-collapse:collapse;margin:20px 0;">
                              <tr style="background:#f9fafb;">
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;color:#6b7280;width:40%;">
                                  Mã kiểm tra
                                </td>
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;font-weight:600;color:#111827;">
                                  {{inspectionId}}
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;color:#6b7280;
                                           border-top:1px solid #e5e7eb;">
                                  Khách thuê
                                </td>
                                <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                           font-size:13px;color:#374151;
                                           border-top:1px solid #e5e7eb;">
                                  {{tenantName}}
                                </td>
                              </tr>
                            </table>
                            <p style="font-family:Arial,sans-serif;font-size:14px;color:#374151;">
                              Vui lòng theo dõi tiến trình kiểm tra trên hệ thống.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:14px 32px;background:#f9fafb;">
                            <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">
                              Email này được gửi tự động. Vui lòng không trả lời trực tiếp.
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """,
                """
                Kính gửi {{managerName}},
                
                Hợp đồng #{{contractId}} của khách {{tenantName}} đã hết hạn.
                
                Mã kiểm tra: {{inspectionId}}
                
                Hệ thống đã tự động phân công nhân viên kiểm tra nhà.
                Vui lòng theo dõi tiến trình trên hệ thống.
                """,
                List.of("managerName", "contractId", "tenantName",
                        "houseId", "inspectionId"),
                "system"
        );

    }

    private void upsertActiveV1(
            EmailTemplateRepository templateRepo,
            EmailTemplateVersionRepository versionRepo,
            String templateKey,
            String category,
            String recipientType,
            LocaleType locale,
            String subjectTpl,
            String htmlTpl,
            String textTpl,
            List<String> allowedVars,
            String actor
    ) {
        EmailTemplate tpl = templateRepo.findByTemplateKey(templateKey)
                .orElseGet(() -> templateRepo.save(
                        EmailTemplate.builder()
                                .templateKey(templateKey)
                                .category(category)
                                .recipientType(recipientType)
                                .createdBy(actor)
                                .updatedBy(actor)
                                .build()
                ));

        boolean hasActive = versionRepo
                .findFirstByTemplate_TemplateKeyAndLocaleAndStatusOrderByVersionDesc(
                        templateKey, locale, TemplateStatus.ACTIVE
                ).isPresent();

        if (hasActive) return;

        EmailTemplateVersion v1 = EmailTemplateVersion.builder()
                .template(tpl)
                .locale(locale)
                .version(1)
                .status(TemplateStatus.ACTIVE)
                .subjectTpl(subjectTpl)
                .htmlTpl(htmlTpl)
                .textTpl(textTpl)
                .allowedVars(allowedVars)
                .createdBy(actor)
                .updatedBy(actor)
                .build();

        versionRepo.save(v1);
    }
}
