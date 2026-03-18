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
