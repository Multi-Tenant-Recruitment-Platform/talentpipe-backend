import { api } from './client';
import type { InviteUserRequest, UserResponse } from './types';

/**
 * Team & invitation endpoints (PB-003 / PB-004). COMPANY_ADMIN only — the
 * backend enforces both the role and the tenant scope, so no tenant is ever
 * sent from here.
 */
export const teamApi = {
  /** Everyone in the caller's workspace: active members and pending invitations. */
  async list(): Promise<UserResponse[]> {
    const { data } = await api.get<UserResponse[]>('/team');
    return data;
  },

  /** Invites an HR manager or interviewer; the new user starts as INVITED. */
  async invite(request: InviteUserRequest): Promise<UserResponse> {
    const { data } = await api.post<UserResponse>('/team/invitations', request);
    return data;
  },

  /** Re-sends the invitation email. */
  async resend(userId: string): Promise<void> {
    await api.post(`/team/invitations/${userId}/resend`);
  },

  /** Revokes a pending invitation and removes the placeholder account. */
  async revoke(userId: string): Promise<void> {
    await api.delete(`/team/invitations/${userId}`);
  },
};
