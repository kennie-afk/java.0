const { URL } = require('url')

function hostPattern(urlString, fallback) {
  try {
    const u = new URL(urlString)
    return {
      protocol: u.protocol.replace(':', ''),
      hostname: u.hostname,
      ...(u.port ? { port: u.port } : {}),
    }
  } catch {
    return fallback
  }
}

const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'
const assetHost = process.env.NEXT_PUBLIC_S3_PUBLIC_URL || 'https://smartre-documents.s3.amazonaws.com'

const remotePatterns = [
  hostPattern(apiUrl, { protocol: 'http', hostname: 'localhost' }),
  hostPattern(assetHost, { protocol: 'https', hostname: 'smartre-documents.s3.amazonaws.com' }),
]

const mediaHosts = remotePatterns
  .map((p) => `${p.protocol}://${p.hostname}${p.port ? ':' + p.port : ''}`)
  .join(' ')

// Where the server itself reaches the API. The public URL is for the browser;
// inside the container `localhost:8080` is the container's own loopback, so
// server-side fetches — the image optimiser above all — need the service name.
const internalApiUrl = process.env.INTERNAL_API_URL || 'http://api-gateway:8080'

const nextConfig = {
  output: 'standalone',
  poweredByHeader: false,
  async rewrites() {
    return [
      // Media is referenced as a root-relative path (see lib/media.ts) so the
      // optimiser can resolve it. This forwards those paths to the gateway.
      { source: '/api/documents/files/:path*', destination: `${internalApiUrl}/api/documents/files/:path*` },
    ]
  },
  images: {
    remotePatterns,
    dangerouslyAllowSVG: true,
    contentDispositionType: 'attachment',
    contentSecurityPolicy: "default-src 'self'; script-src 'none'; sandbox;",
  },
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=(self)' },
          { key: 'Content-Security-Policy', value: `default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://maps.googleapis.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob: ${mediaHosts}; connect-src 'self' https://maps.googleapis.com ${mediaHosts}; frame-src 'self' https://www.google.com;` },
        ],
      },
    ]
  },
}
module.exports = nextConfig
