// functions/delete-notification/index.ts
// Authenticated user (patient session or doctor) deletes one of their own
// notification rows. The auth identity (userId for doctors, sessionId for
// patients) must match the notifications.user_id on the row, otherwise the
// delete is rejected.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    const ownerId = auth.userId ?? auth.sessionId;
    if (!ownerId) throw new ValidationError("Authentication required");

    const body = await req.json().catch(() => ({} as Record<string, unknown>));
    const notificationId = body.notification_id as string | undefined;
    if (!notificationId) throw new ValidationError("notification_id is required");

    const supabase = getServiceClient();
    const { data: row } = await supabase
      .from("notifications")
      .select("notification_id, user_id")
      .eq("notification_id", notificationId)
      .maybeSingle();
    if (!row) {
      return successResponse({ ok: true, already_gone: true }, 200, origin);
    }
    if (row.user_id !== ownerId) {
      throw new ValidationError("Not authorised to delete this notification");
    }

    await supabase
      .from("notifications")
      .delete()
      .eq("notification_id", notificationId);

    return successResponse({ ok: true }, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
