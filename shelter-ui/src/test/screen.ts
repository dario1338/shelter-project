import { setScreen } from './setup'

// The screen of the next render: a phone, or a wide screen. It is an ordinary one again after the test.
export const narrowScreen = () => setScreen({ narrow: true })
export const veryWideScreen = () => setScreen({ veryWide: true })
