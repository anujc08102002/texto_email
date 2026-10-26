export const corporateOrigin = (process.env.NEXT_PUBLIC_WHATSAPP_BASE_URL || "https://texto.in").replace(/\/$/, "");
export const corporateUrl = (path: string) => `${corporateOrigin}${path}`;
