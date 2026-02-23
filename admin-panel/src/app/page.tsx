export const dynamic = "force-dynamic";

import { redirect } from "next/navigation";
import { checkAdminExists } from "@/lib/actions";

export default async function Home() {
  const { exists } = await checkAdminExists();

  if (!exists) {
    redirect("/setup");
  }

  redirect("/login");
}
