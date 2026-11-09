// The paw of the shelter, a mark next to its name. It only decorates, so a screen reader skips it.
export function PawIcon({ size = 20 }: Readonly<{ size?: number }>) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" style={{ flexShrink: 0 }}>
      <ellipse cx="6.5" cy="10" rx="2" ry="2.6" />
      <ellipse cx="10.5" cy="6.2" rx="2" ry="2.6" />
      <ellipse cx="15.5" cy="6.2" rx="2" ry="2.6" />
      <ellipse cx="19" cy="10" rx="2" ry="2.6" />
      <path d="M12.9 11.2c-2.7 0-5.9 3.3-5.9 6 0 1.7 1.3 2.5 2.9 2.5 1 0 1.8-.5 3-.5s2 .5 3 .5c1.6 0 2.9-.8 2.9-2.5 0-2.7-3.2-6-5.9-6z" />
    </svg>
  )
}
