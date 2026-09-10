import { redirect } from "next/navigation";

/** Public self-serve registration is disabled. Admins provision accounts. */
export default function RegisterPage() {
  redirect("/login");
}
