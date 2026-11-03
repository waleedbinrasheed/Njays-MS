import { Suspense } from "react";
import { AuthCard } from "@/features/auth/AuthCard";
import { LoginForm } from "@/features/auth/LoginForm";

export default function LoginPage() {
  return (
    <AuthCard title="Sign in" subtitle="Tailoring Management System">
      <Suspense>
        <LoginForm />
      </Suspense>
    </AuthCard>
  );
}
