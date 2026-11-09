import QRCode from 'qrcode';

export async function generateQrSvg(text, options = {}) {
  try {
    const defaultOpts = {
      type: 'svg',
      margin: 1,
      width: options.width || 220,
      color: {
        dark: options.darkColor || '#0b0f19',
        light: options.lightColor || '#ffffff'
      }
    };
    return await QRCode.toString(text, defaultOpts);
  } catch (err) {
    console.warn('QR generation error:', err);
    return `<div class="qr-fallback">QR: ${text}</div>`;
  }
}
