/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend API base. Defaults to '/api/v1' (served through the Vite proxy). */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
