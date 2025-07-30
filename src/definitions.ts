export interface CapacitorTwilioVoicePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
