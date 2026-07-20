import { Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { DashboardLayout } from './layouts/DashboardLayout';
import { AcceptInvitePage } from './pages/AcceptInvitePage';
import { CandidateRegisterPage } from './pages/CandidateRegisterPage';
import { CompanySettingsPage } from './pages/dashboard/CompanySettingsPage';
import { OverviewPage } from './pages/dashboard/OverviewPage';
import { PipelinePage } from './pages/dashboard/PipelinePage';
import { TeamPage } from './pages/dashboard/TeamPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { JobsPage } from './pages/JobsPage';
import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* Public + auth pages share the marketing chrome. */}
        <Route element={<Layout />}>
          <Route path="/" element={<LandingPage />} />
          <Route path="/jobs" element={<JobsPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/register-candidate" element={<CandidateRegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          {/* Landing pages for emailed links — the token arrives in the query string. */}
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/accept-invite" element={<AcceptInvitePage />} />
        </Route>

        {/* Company admin dashboard gets its own full-screen chrome. */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <DashboardLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<OverviewPage />} />
          <Route path="team" element={<TeamPage />} />
          <Route path="pipeline" element={<PipelinePage />} />
          <Route path="settings" element={<CompanySettingsPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}
