/**
 * YAiPlayer Web — TV Pairing Companion Adapter
 * Implements the companion claim workflow: confirming a Smart TV / STB device via 6-digit PIN.
 */

import { safeFetch } from './apiConfig.js';
import { authAdapter } from './authAdapter.js';

export async function claimDevicePairing(code, signal) {
  const cleanCode = (code || '').trim();
  if (!/^\d{6}$/.test(cleanCode)) {
    throw new Error('Код привязки должен состоять ровно из 6 цифр.');
  }

  const token = authAdapter.getToken();
  if (!token) {
    throw new Error('Для подтверждения привязки необходимо войти в аккаунт YAiPlayer.');
  }

  const epoch = authAdapter.getEpoch();
  const res = await safeFetch('pairing/claim', {
    method: 'POST',
    body: { code: cleanCode },
    token,
    signal,
  });

  if (signal?.aborted || epoch !== authAdapter.getEpoch() || token !== authAdapter.getToken()) throw new Error('Сессия изменилась');
  if (!res || res.success !== true) {
    throw new Error('Не удалось подтвердить привязку устройства.');
  }

  return {
    success: true,
    deviceId: typeof res.deviceId === 'string' ? res.deviceId.slice(0, 128) : 'Unknown Device',
    deviceName: typeof res.deviceName === 'string' ? res.deviceName.slice(0, 128) : 'Smart TV STB',
  };
}
