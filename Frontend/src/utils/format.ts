/** Shared display formatting helpers. */

/** COMPANY_ADMIN → "Company Admin", HR_MANAGER → "HR Manager", etc. */
export function formatRole(role: string): string {
  switch (role) {
    case 'COMPANY_ADMIN':
      return 'Company Admin';
    case 'HR_MANAGER':
      return 'HR Manager';
    case 'INTERVIEWER':
      return 'Interviewer';
    case 'CANDIDATE':
      return 'Candidate';
    default:
      return role
        .toLowerCase()
        .split('_')
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
  }
}
