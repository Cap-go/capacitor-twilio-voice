import { registerPlugin } from '@capacitor/core';
const CapacitorTwilioVoice = registerPlugin('CapacitorTwilioVoice', {
    web: () => import('./web').then((m) => new m.CapacitorTwilioVoiceWeb()),
});
export * from './definitions';
export { CapacitorTwilioVoice };
//# sourceMappingURL=index.js.map