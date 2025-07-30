import { WebPlugin } from '@capacitor/core';

import type { CapacitorTwilioVoicePlugin } from './definitions';

export class CapacitorTwilioVoiceWeb extends WebPlugin implements CapacitorTwilioVoicePlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
