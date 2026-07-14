/**
 * Token storage for the SPA.
 *
 * - ACCESS token: in memory only — it is short-lived (15 min) and keeping it
 *   out of storage removes the easiest XSS exfiltration target.
 * - REFRESH token: localStorage so a page reload can restore the session.
 *   NOTE: production may move the refresh token to an httpOnly cookie set by
 *   the backend, which JavaScript (and therefore XSS) cannot read. Kept
 *   simple and honest for Week 1.
 */

const REFRESH_TOKEN_KEY = 'talentpipe.refreshToken';

let accessToken: string | null = null;

export const tokenStore = {
  getAccessToken(): string | null {
    return accessToken;
  },

  setAccessToken(token: string | null): void {
    accessToken = token;
  },

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },

  setRefreshToken(token: string): void {
    localStorage.setItem(REFRESH_TOKEN_KEY, token);
  },

  clear(): void {
    accessToken = null;
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};
