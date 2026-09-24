import { Suspense } from "react";
import { AuthCard } from "@/features/auth/AuthCard";
import { ChangePasswordForm } from "@/features/auth/ChangePasswordForm";

export default function ChangePasswordPage() {
  return (
    <AuthCard title="Change password">
      <Suspense>
        <ChangePasswordForm />
      </Suspense>
    </AuthCard>
  );
}
