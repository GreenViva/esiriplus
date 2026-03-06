// _shared/resend.ts
// Send emails via the Resend API.

const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY");

export async function sendOtpEmail(
  email: string,
  otp: string,
  fullName?: string
): Promise<void> {
  if (!RESEND_API_KEY) {
    console.error("RESEND_API_KEY not configured — skipping email send");
    return;
  }

  const greeting = fullName ? `Hello ${fullName}` : "Hello";

  const html = `
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"></head>
<body style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px;">
  <div style="text-align: center; margin-bottom: 24px;">
    <h2 style="color: #2A9D8F; margin: 0;">eSIRI Plus</h2>
  </div>
  <p>${greeting},</p>
  <p>Your verification code is:</p>
  <div style="text-align: center; margin: 24px 0;">
    <span style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #2A9D8F;">${otp}</span>
  </div>
  <p>This code expires in <strong>10 minutes</strong>.</p>
  <p>If you did not request this code, please ignore this email.</p>
  <hr style="border: none; border-top: 1px solid #eee; margin: 24px 0;">
  <p style="font-size: 12px; color: #888; text-align: center;">&copy; eSIRI Plus</p>
</body>
</html>`;

  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: "eSIRI Plus <noreply@esiri.africa>",
      to: [email],
      subject: `${otp} is your eSIRI Plus verification code`,
      html,
    }),
  });

  if (!res.ok) {
    const body = await res.text();
    console.error("Resend API error:", res.status, body);
    throw new Error("Failed to send verification email");
  }

  console.log(`OTP email sent to ${email}`);
}
