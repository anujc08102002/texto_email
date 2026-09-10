export const duration = {
  fast: 0.14,
  base: 0.2,
  slow: 0.28,
} as const;

export const easeOut = [0.22, 1, 0.36, 1] as const;

export const fadeIn = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: duration.base, ease: easeOut } },
};

export const fadeInUp = {
  hidden: { opacity: 0, y: 8 },
  visible: { opacity: 1, y: 0, transition: { duration: duration.base, ease: easeOut } },
};

export const scaleIn = {
  hidden: { opacity: 0, scale: 0.98 },
  visible: { opacity: 1, scale: 1, transition: { duration: duration.fast, ease: easeOut } },
};

export const pageTransition = {
  hidden: { opacity: 0, y: 6 },
  visible: { opacity: 1, y: 0, transition: { duration: duration.base, ease: easeOut } },
};
