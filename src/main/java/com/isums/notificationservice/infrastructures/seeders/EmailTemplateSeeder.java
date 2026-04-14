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
                "Chào mừng {{name}} đến với ISUMS",
                """
                        <!doctype html>
                        <html lang="vi">
                        <head>
                          <meta charset="utf-8">
                          <meta name="viewport" content="width=device-width,initial-scale=1">
                          <title>Chào mừng đến với ISUMS</title>
                        </head>
                        <body style="margin:0;padding:0;background:#f6f7fb;">
                          <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                            Chào mừng {{name}}! Tài khoản ISUMS của bạn đã sẵn sàng.
                          </div>

                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                                 style="background:#f6f7fb;padding:24px 12px;">
                            <tr>
                              <td align="center">
                                <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                                       style="width:100%;max-width:600px;background:#ffffff;border-radius:16px;
                                              overflow:hidden;box-shadow:0 6px 18px rgba(18,38,63,.08);">

                                  <tr>
                                    <td style="padding:28px 32px;background:#0b5cff;color:#ffffff;">
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:22px;font-weight:700;line-height:1.2;">
                                        🎉 Chào mừng đến với ISUMS
                                      </div>
                                      <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;opacity:.9;margin-top:6px;">
                                        Hệ thống quản lý nhà trọ thông minh
                                      </div>
                                    </td>
                                  </tr>

                                  <tr>
                                    <td style="padding:28px 32px;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                                      <p style="margin:0 0 12px;font-size:16px;">
                                        Xin chào <strong>{{name}}</strong>,
                                      </p>
                                      <p style="margin:0 0 16px;font-size:15px;line-height:1.55;">
                                        Tài khoản ISUMS của bạn đã được kích hoạt thành công. Bắt đầu hành trình
                                        quản lý nhà trọ tiện lợi ngay hôm nay.
                                      </p>

                                      <table role="presentation" cellpadding="0" cellspacing="0" style="margin:20px 0;">
                                        <tr>
                                          <td style="background:#0b5cff;border-radius:10px;">
                                            <a href="{{appUrl}}"
                                               style="display:inline-block;padding:12px 24px;font-family:Arial,sans-serif;
                                                      font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;">
                                              Truy cập ISUMS
                                            </a>
                                          </td>
                                        </tr>
                                      </table>

                                      <p style="margin:16px 0 0;font-size:13px;color:#6b7280;line-height:1.5;">
                                        Nếu nút không hoạt động, hãy sao chép liên kết sau vào trình duyệt:<br>
                                        <a href="{{appUrl}}" style="color:#0b5cff;word-break:break-all;">{{appUrl}}</a>
                                      </p>
                                    </td>
                                  </tr>

                                  <tr>
                                    <td style="padding:16px 32px;background:#f9fafb;
                                               font-family:Arial,sans-serif;font-size:12px;color:#9ca3af;text-align:center;">
                                      Cần hỗ trợ? Liên hệ <a href="mailto:{{supportEmail}}" style="color:#0b5cff;text-decoration:none;">{{supportEmail}}</a><br>
                                      Email này được gửi tự động, vui lòng không trả lời.
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
                        Truy cập ngay: {{appUrl}}

                        Cần hỗ trợ? Liên hệ {{supportEmail}}
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

        // ── CONTRACT RENEWAL REMINDER (tenant) ────────────────────────────
        upsertActiveV1(
                templateRepo, versionRepo,
                "contract_renewal_reminder", "CONTRACT", "TENANT",
                LocaleType.vi_VN,
                "Hợp đồng của bạn còn {{daysRemaining}} ngày — Bạn có muốn gia hạn?",
                """
                        <!doctype html>
                        <html lang="vi">
                        <head><meta charset="utf-8"></head>
                        <body style="margin:0;padding:0;background:#f3f4f6;">
                          <table width="100%" cellpadding="0" cellspacing="0">
                            <tr><td align="center" style="padding:32px 16px;">
                              <table width="600" cellpadding="0" cellspacing="0"
                                     style="background:#ffffff;border-radius:12px;overflow:hidden;
                                            box-shadow:0 1px 4px rgba(0,0,0,.08);">
                                <tr>
                                  <td style="padding:28px 32px;background:#d97706;">
                                    <div style="font-family:Arial,sans-serif;font-size:20px;
                                                font-weight:700;color:#ffffff;">
                                      ⏰ Hợp đồng sắp hết hạn
                                    </div>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:28px 32px;">
                                    <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                      Kính gửi <strong>{{tenantName}}</strong>,
                                    </p>
                                    <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                      Hợp đồng thuê nhà <strong>#{{contractId}}</strong> của bạn
                                      {{#openForNew}}
                                        đã hết hạn hôm nay. Phòng đã được mở cho khách mới đặt cọc.
                                      {{/openForNew}}
                                      {{^openForNew}}
                                        còn <strong>{{daysRemaining}} ngày</strong> nữa sẽ hết hạn vào
                                        <strong>{{endDate}}</strong>.
                                      {{/openForNew}}
                                    </p>
                                    <p style="font-family:Arial,sans-serif;font-size:14px;color:#374151;">
                                      Nếu bạn muốn tiếp tục thuê, vui lòng liên hệ quản lý hoặc
                                      bấm nút <strong>Gia hạn</strong> trong ứng dụng ISUMS.
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
                        Kính gửi {{tenantName}},
                        
                        Hợp đồng #{{contractId}} của bạn còn {{daysRemaining}} ngày (hết hạn {{endDate}}).
                        
                        Nếu muốn gia hạn, vui lòng liên hệ quản lý hoặc bấm Gia hạn trong app ISUMS.
                        """,
                List.of("tenantName", "contractId", "daysRemaining", "endDate", "openForNew"),
                "system"
        );

// ── RENEWAL REQUEST RECEIVED (manager) ────────────────────────────
        upsertActiveV1(
                templateRepo, versionRepo,
                "renewal_request_received", "CONTRACT", "MANAGER",
                LocaleType.vi_VN,
                "Khách {{tenantName}} muốn gia hạn hợp đồng #{{contractId}}",
                """
                        <!doctype html>
                        <html lang="vi">
                        <head><meta charset="utf-8"></head>
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
                                      🔔 Yêu cầu gia hạn hợp đồng
                                    </div>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:28px 32px;">
                                    <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                      Kính gửi <strong>{{managerName}}</strong>,
                                    </p>
                                    <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                      Khách <strong>{{tenantName}}</strong> vừa gửi yêu cầu gia hạn
                                      hợp đồng <strong>#{{contractId}}</strong>.
                                    </p>
                                    <table width="100%" style="border:1px solid #e5e7eb;
                                           border-radius:8px;border-collapse:collapse;margin:20px 0;">
                                      <tr style="background:#f9fafb;">
                                        <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                                   font-size:13px;color:#6b7280;width:40%;">
                                          Tình trạng cạnh tranh
                                        </td>
                                        <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                                   font-size:13px;font-weight:600;color:#dc2626;">
                                          {{hasCompetingDeposit}}
                                        </td>
                                      </tr>
                                      <tr>
                                        <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                                   font-size:13px;color:#6b7280;
                                                   border-top:1px solid #e5e7eb;">
                                          Ghi chú của khách
                                        </td>
                                        <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                                   font-size:13px;color:#374151;
                                                   border-top:1px solid #e5e7eb;">
                                          {{note}}
                                        </td>
                                      </tr>
                                    </table>
                                    <p style="font-family:Arial,sans-serif;font-size:14px;color:#374151;">
                                      Vui lòng đăng nhập hệ thống để liên hệ khách và soạn hợp đồng mới nếu đồng ý.
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
                        
                        Khách {{tenantName}} vừa gửi yêu cầu gia hạn hợp đồng #{{contractId}}.
                        
                        Tình trạng cạnh tranh: {{hasCompetingDeposit}}
                        Ghi chú: {{note}}
                        
                        Vui lòng đăng nhập hệ thống để xử lý.
                        """,
                List.of("managerName", "tenantName", "contractId", "hasCompetingDeposit", "note"),
                "system"
        );

// ── RENEWAL DECLINED (tenant) ──────────────────────────────────────
        upsertActiveV1(
                templateRepo, versionRepo,
                "renewal_declined", "CONTRACT", "TENANT",
                LocaleType.vi_VN,
                "Yêu cầu gia hạn hợp đồng #{{contractId}} không được chấp thuận",
                """
                        <!doctype html>
                        <html lang="vi">
                        <head><meta charset="utf-8"></head>
                        <body style="margin:0;padding:0;background:#f3f4f6;">
                          <table width="100%" cellpadding="0" cellspacing="0">
                            <tr><td align="center" style="padding:32px 16px;">
                              <table width="600" cellpadding="0" cellspacing="0"
                                     style="background:#ffffff;border-radius:12px;overflow:hidden;
                                            box-shadow:0 1px 4px rgba(0,0,0,.08);">
                                <tr>
                                  <td style="padding:28px 32px;background:#991b1b;">
                                    <div style="font-family:Arial,sans-serif;font-size:20px;
                                                font-weight:700;color:#ffffff;">
                                      ❌ Yêu cầu gia hạn không được chấp thuận
                                    </div>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:28px 32px;">
                                    <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                      Kính gửi <strong>{{tenantName}}</strong>,
                                    </p>
                                    <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                      Rất tiếc, yêu cầu gia hạn hợp đồng <strong>#{{contractId}}</strong>
                                      của bạn không được chấp thuận.
                                    </p>
                                    <table width="100%" style="border:1px solid #e5e7eb;
                                           border-radius:8px;border-collapse:collapse;margin:20px 0;">
                                      <tr style="background:#f9fafb;">
                                        <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                                   font-size:13px;color:#6b7280;width:40%;">
                                          Lý do
                                        </td>
                                        <td style="padding:10px 16px;font-family:Arial,sans-serif;
                                                   font-size:13px;color:#374151;">
                                          {{reason}}
                                        </td>
                                      </tr>
                                    </table>
                                    <p style="font-family:Arial,sans-serif;font-size:14px;color:#374151;">
                                      Nếu có thắc mắc, vui lòng liên hệ quản lý để được hỗ trợ.
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
                        Kính gửi {{tenantName}},
                        
                        Yêu cầu gia hạn hợp đồng #{{contractId}} của bạn không được chấp thuận.
                        
                        Lý do: {{reason}}
                        
                        Nếu có thắc mắc, vui lòng liên hệ quản lý.
                        """,
                List.of("tenantName", "contractId", "reason"),
                "system"
        );

        // late_payment_reminder_day0
        upsertActiveV1(templateRepo, versionRepo,
                "late_payment_reminder_day0", "PAYMENT", "TENANT", LocaleType.vi_VN,
                "Nhắc nhở: Hóa đơn tiền thuê đến hạn hôm nay",
                """
                        <!doctype html><html lang="vi"><head><meta charset="utf-8"></head>
                        <body style="margin:0;padding:0;background:#f3f4f6;">
                          <table width="100%" cellpadding="0" cellspacing="0">
                            <tr><td align="center" style="padding:32px 16px;">
                              <table width="600" cellpadding="0" cellspacing="0"
                                     style="background:#fff;border-radius:12px;overflow:hidden;
                                            box-shadow:0 1px 4px rgba(0,0,0,.08);">
                                <tr><td style="padding:28px 32px;background:#2563eb;">
                                  <div style="font-family:Arial,sans-serif;font-size:20px;font-weight:700;color:#fff;">
                                    💳 Hóa đơn tiền thuê đến hạn
                                  </div>
                                </td></tr>
                                <tr><td style="padding:28px 32px;">
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                    Hóa đơn tiền thuê tháng này đến hạn thanh toán hôm nay
                                    (<strong>{{dueDate}}</strong>).
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                    Số tiền: <strong>{{totalAmount}}</strong>
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:14px;color:#6b7280;">
                                    Vui lòng thanh toán đúng hạn để tránh phát sinh phí phạt.
                                  </p>
                                </td></tr>
                                <tr><td style="padding:14px 32px;background:#f9fafb;">
                                  <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">
                                    Email này được gửi tự động. Vui lòng không trả lời trực tiếp.
                                  </div>
                                </td></tr>
                              </table>
                            </td></tr>
                          </table>
                        </body></html>
                        """,
                "Hóa đơn tiền thuê tháng này đến hạn hôm nay ({{dueDate}}).\nSố tiền: {{totalAmount}}\nVui lòng thanh toán đúng hạn.",
                List.of("totalAmount", "dueDate", "daysLate"),
                "system"
        );

// late_payment_reminder_day1
        upsertActiveV1(templateRepo, versionRepo,
                "late_payment_reminder_day1", "PAYMENT", "TENANT", LocaleType.vi_VN,
                "Nhắc lần 2: Hóa đơn tiền thuê quá hạn 1 ngày",
                """
                        <!doctype html><html lang="vi"><head><meta charset="utf-8"></head>
                        <body style="margin:0;padding:0;background:#f3f4f6;">
                          <table width="100%" cellpadding="0" cellspacing="0">
                            <tr><td align="center" style="padding:32px 16px;">
                              <table width="600" cellpadding="0" cellspacing="0"
                                     style="background:#fff;border-radius:12px;overflow:hidden;">
                                <tr><td style="padding:28px 32px;background:#d97706;">
                                  <div style="font-family:Arial,sans-serif;font-size:20px;font-weight:700;color:#fff;">
                                    ⚠️ Hóa đơn quá hạn 1 ngày
                                  </div>
                                </td></tr>
                                <tr><td style="padding:28px 32px;">
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                    Hóa đơn tiền thuê của bạn đã quá hạn <strong>1 ngày</strong>.
                                    Số tiền cần thanh toán: <strong>{{totalAmount}}</strong>.
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:14px;color:#6b7280;">
                                    Sau 3 ngày quá hạn, hệ thống sẽ tự động áp dụng phí phạt trễ thanh toán.
                                  </p>
                                </td></tr>
                                <tr><td style="padding:14px 32px;background:#f9fafb;">
                                  <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">Email này được gửi tự động.</div>
                                </td></tr>
                              </table>
                            </td></tr>
                          </table>
                        </body></html>
                        """,
                "Hóa đơn tiền thuê quá hạn 1 ngày. Số tiền: {{totalAmount}}. Thanh toán ngay để tránh phạt.",
                List.of("totalAmount", "dueDate", "daysLate"),
                "system"
        );

// late_payment_reminder_day2
        upsertActiveV1(templateRepo, versionRepo,
                "late_payment_reminder_day2", "PAYMENT", "TENANT", LocaleType.vi_VN,
                "Cảnh báo: Hóa đơn tiền thuê quá hạn 2 ngày — còn 1 ngày trước khi bị phạt",
                """
                        <!doctype html><html lang="vi"><head><meta charset="utf-8"></head>
                        <body style="margin:0;padding:0;background:#f3f4f6;">
                          <table width="100%" cellpadding="0" cellspacing="0">
                            <tr><td align="center" style="padding:32px 16px;">
                              <table width="600" cellpadding="0" cellspacing="0"
                                     style="background:#fff;border-radius:12px;overflow:hidden;">
                                <tr><td style="padding:28px 32px;background:#dc2626;">
                                  <div style="font-family:Arial,sans-serif;font-size:20px;font-weight:700;color:#fff;">
                                    🚨 Còn 1 ngày trước khi bị phạt trễ thanh toán
                                  </div>
                                </td></tr>
                                <tr><td style="padding:28px 32px;">
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                    Hóa đơn tiền thuê của bạn đã quá hạn <strong>2 ngày</strong>.
                                    Số tiền: <strong>{{totalAmount}}</strong>.
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#dc2626;font-weight:600;">
                                    Nếu chưa thanh toán sau ngày mai, hệ thống sẽ áp dụng phí phạt 5% tiền thuê tháng.
                                  </p>
                                </td></tr>
                                <tr><td style="padding:14px 32px;background:#f9fafb;">
                                  <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">Email này được gửi tự động.</div>
                                </td></tr>
                              </table>
                            </td></tr>
                          </table>
                        </body></html>
                        """,
                "CẢNH BÁO: Hóa đơn quá hạn 2 ngày. Còn 1 ngày trước khi bị phạt 5%. Số tiền: {{totalAmount}}.",
                List.of("totalAmount", "dueDate", "daysLate"),
                "system"
        );

// late_payment_penalty_applied
        upsertActiveV1(templateRepo, versionRepo,
                "late_payment_penalty_applied", "PAYMENT", "TENANT", LocaleType.vi_VN,
                "Thông báo: Áp dụng phí phạt trễ thanh toán {{penaltyPercent}}%",
                """
                        <!doctype html><html lang="vi"><head><meta charset="utf-8"></head>
                        <body style="margin:0;padding:0;background:#f3f4f6;">
                          <table width="100%" cellpadding="0" cellspacing="0">
                            <tr><td align="center" style="padding:32px 16px;">
                              <table width="600" cellpadding="0" cellspacing="0"
                                     style="background:#fff;border-radius:12px;overflow:hidden;">
                                <tr><td style="padding:28px 32px;background:#991b1b;">
                                  <div style="font-family:Arial,sans-serif;font-size:20px;font-weight:700;color:#fff;">
                                    💸 Phí phạt trễ thanh toán đã được áp dụng
                                  </div>
                                </td></tr>
                                <tr><td style="padding:28px 32px;">
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                    Do thanh toán trễ <strong>{{daysLate}} ngày</strong>, phí phạt
                                    <strong>{{penaltyPercent}}%</strong> đã được áp dụng vào hóa đơn của bạn.
                                  </p>
                                  <table width="100%" style="border:1px solid #e5e7eb;border-radius:8px;
                                         border-collapse:collapse;margin:20px 0;">
                                    <tr style="background:#f9fafb;">
                                      <td style="padding:10px 16px;font-size:13px;color:#6b7280;width:50%;">Phí phạt</td>
                                      <td style="padding:10px 16px;font-size:13px;font-weight:600;color:#dc2626;">{{penaltyAmount}}</td>
                                    </tr>
                                    <tr>
                                      <td style="padding:10px 16px;font-size:13px;color:#6b7280;border-top:1px solid #e5e7eb;">Tổng cần thanh toán</td>
                                      <td style="padding:10px 16px;font-size:13px;font-weight:700;color:#111827;border-top:1px solid #e5e7eb;">{{totalAmount}}</td>
                                    </tr>
                                  </table>
                                  <p style="font-family:Arial,sans-serif;font-size:14px;color:#374151;">
                                    Vui lòng thanh toán ngay để tránh phát sinh thêm phí phạt.
                                  </p>
                                </td></tr>
                                <tr><td style="padding:14px 32px;background:#f9fafb;">
                                  <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">Email này được gửi tự động.</div>
                                </td></tr>
                              </table>
                            </td></tr>
                          </table>
                        </body></html>
                        """,
                "Phí phạt {{penaltyPercent}}% đã được áp dụng do trễ {{daysLate}} ngày.\nPhí phạt: {{penaltyAmount}}\nTổng cần thanh toán: {{totalAmount}}",
                List.of("penaltyPercent", "penaltyAmount", "totalAmount", "daysLate"),
                "system"
        );

// late_payment_formal_warning
        upsertActiveV1(templateRepo, versionRepo,
                "late_payment_formal_warning", "PAYMENT", "TENANT", LocaleType.vi_VN,
                "Cảnh báo chính thức: Hóa đơn tiền thuê quá hạn 7 ngày — Tính năng app bị hạn chế",
                """
                        <!doctype html><html lang="vi"><head><meta charset="utf-8"></head>
                        <body style="margin:0;padding:0;background:#f3f4f6;">
                          <table width="100%" cellpadding="0" cellspacing="0">
                            <tr><td align="center" style="padding:32px 16px;">
                              <table width="600" cellpadding="0" cellspacing="0"
                                     style="background:#fff;border-radius:12px;overflow:hidden;">
                                <tr><td style="padding:28px 32px;background:#1f2937;">
                                  <div style="font-family:Arial,sans-serif;font-size:20px;font-weight:700;color:#fff;">
                                    🔒 Cảnh báo chính thức — Tài khoản bị hạn chế
                                  </div>
                                </td></tr>
                                <tr><td style="padding:28px 32px;">
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                    Hóa đơn tiền thuê của bạn đã quá hạn <strong>7 ngày</strong>.
                                    Tổng số tiền cần thanh toán: <strong>{{totalAmount}}</strong>.
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#dc2626;font-weight:600;">
                                    Tính năng ứng dụng của bạn đã bị hạn chế cho đến khi hoàn tất thanh toán.
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:14px;color:#374151;">
                                    Nếu không thanh toán trong thời gian sớm, chủ nhà có quyền thực hiện
                                    các biện pháp mạnh hơn theo quy định hợp đồng.
                                  </p>
                                </td></tr>
                                <tr><td style="padding:14px 32px;background:#f9fafb;">
                                  <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">Email này được gửi tự động.</div>
                                </td></tr>
                              </table>
                            </td></tr>
                          </table>
                        </body></html>
                        """,
                "CẢNH BÁO CHÍNH THỨC: Hóa đơn quá hạn 7 ngày. Tài khoản bị hạn chế.\nTổng tiền: {{totalAmount}}\nVui lòng thanh toán ngay.",
                List.of("totalAmount", "dueDate", "daysLate"),
                "system"
        );

// power_cut_warning_24h
        upsertActiveV1(templateRepo, versionRepo,
                "power_cut_warning_24h", "PAYMENT", "TENANT", LocaleType.vi_VN,
                "Cảnh báo: Điện sẽ bị cắt sau 24 giờ do chưa thanh toán tiền thuê",
                """
                        <!doctype html><html lang="vi"><head><meta charset="utf-8"></head>
                        <body style="margin:0;padding:0;background:#f3f4f6;">
                          <table width="100%" cellpadding="0" cellspacing="0">
                            <tr><td align="center" style="padding:32px 16px;">
                              <table width="600" cellpadding="0" cellspacing="0"
                                     style="background:#fff;border-radius:12px;overflow:hidden;">
                                <tr><td style="padding:28px 32px;background:#7c2d12;">
                                  <div style="font-family:Arial,sans-serif;font-size:20px;font-weight:700;color:#fff;">
                                    ⚡ Cảnh báo cắt điện sau 24 giờ
                                  </div>
                                </td></tr>
                                <tr><td style="padding:28px 32px;">
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                    Do chưa thanh toán tiền thuê, chủ nhà đã xác nhận cắt điện.
                                    Điện sẽ bị cắt vào lúc <strong>{{executeAt}}</strong>.
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#dc2626;font-weight:600;">
                                    Vui lòng thanh toán ngay để tránh bị cắt điện.
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:13px;color:#6b7280;">
                                    Đây là thông báo bắt buộc theo quy định hợp đồng thuê nhà.
                                  </p>
                                </td></tr>
                                <tr><td style="padding:14px 32px;background:#f9fafb;">
                                  <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">Email này được gửi tự động.</div>
                                </td></tr>
                              </table>
                            </td></tr>
                          </table>
                        </body></html>
                        """,
                "CẢNH BÁO: Điện sẽ bị cắt vào {{executeAt}} do chưa thanh toán tiền thuê.\nVui lòng thanh toán ngay để tránh bị cắt điện.",
                List.of("executeAt"),
                "system"
        );

// overdue_termination_notice
        upsertActiveV1(templateRepo, versionRepo,
                "overdue_termination_notice", "PAYMENT", "MANAGER", LocaleType.vi_VN,
                "Thông báo: Khách {{tenantName}} trễ tiền thuê 30 ngày — Xem xét chấm dứt hợp đồng",
                """
                        <!doctype html><html lang="vi"><head><meta charset="utf-8"></head>
                        <body style="margin:0;padding:0;background:#f3f4f6;">
                          <table width="100%" cellpadding="0" cellspacing="0">
                            <tr><td align="center" style="padding:32px 16px;">
                              <table width="600" cellpadding="0" cellspacing="0"
                                     style="background:#fff;border-radius:12px;overflow:hidden;">
                                <tr><td style="padding:28px 32px;background:#1f2937;">
                                  <div style="font-family:Arial,sans-serif;font-size:20px;font-weight:700;color:#fff;">
                                    📋 Khách trễ tiền thuê 30 ngày
                                  </div>
                                </td></tr>
                                <tr><td style="padding:28px 32px;">
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                    Kính gửi <strong>{{managerName}}</strong>,
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:15px;color:#374151;">
                                    Khách <strong>{{tenantName}}</strong> (Hợp đồng #{{contractId}}) đã
                                    chậm thanh toán tiền thuê <strong>30 ngày</strong>.
                                  </p>
                                  <p style="font-family:Arial,sans-serif;font-size:14px;color:#374151;">
                                    Theo Luật Nhà ở 2023, bạn có quyền khởi động thủ tục chấm dứt hợp đồng.
                                    Vui lòng đăng nhập hệ thống để xem xét và quyết định.
                                  </p>
                                </td></tr>
                                <tr><td style="padding:14px 32px;background:#f9fafb;">
                                  <div style="font-family:Arial,sans-serif;font-size:11px;color:#9ca3af;">Email này được gửi tự động.</div>
                                </td></tr>
                              </table>
                            </td></tr>
                          </table>
                        </body></html>
                        """,
                "Kính gửi {{managerName}},\nKhách {{tenantName}} (HĐ #{{contractId}}) đã trễ tiền thuê 30 ngày.\nVui lòng đăng nhập hệ thống để xem xét chấm dứt hợp đồng.",
                List.of("managerName", "tenantName", "contractId", "daysLate"),
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
