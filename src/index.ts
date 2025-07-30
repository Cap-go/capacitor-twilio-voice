import { registerPlugin } from '@capacitor/core';

import type { CapacitorTwilioVoicePlugin } from './definitions';

const CapacitorTwilioVoice = registerPlugin<CapacitorTwilioVoicePlugin>('CapacitorTwilioVoice', {
  web: () => import('./web').then((m) => new m.CapacitorTwilioVoiceWeb()),
});

export * from './definitions';
export { CapacitorTwilioVoice };
