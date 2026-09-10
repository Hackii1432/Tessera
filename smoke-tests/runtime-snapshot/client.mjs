// Offline, loopback-only protocol clients. Packet IDs come from this checkout,
// not an external protocol library. These clients do not render the world.
import net from 'node:net';
import {inflateSync} from 'node:zlib';
import {readFileSync} from 'node:fs';
import path from 'node:path';

const vi = n => { const a = []; do { let b = n & 127; n >>>= 7; if (n) b |= 128; a.push(b); } while (n); return Buffer.from(a); };
const str = s => Buffer.concat([vi(Buffer.byteLength(s)), Buffer.from(s)]);
function readVi(buf, offset = 0) {
  let value = 0;
  for (let i = 0; i < 5; i++) {
    if (offset + i >= buf.length) return null;
    const b = buf[offset + i]; value |= (b & 127) << (7 * i);
    if (!(b & 128)) return {value, end: offset + i + 1};
  }
  throw Error('Oversized varint');
}
function packetMap(repo, protocol, direction) {
  const source = readFileSync(path.join(repo, 'folia-server/src/minecraft/java/net/minecraft/network/protocol', protocol), 'utf8');
  const names = [...source.matchAll(/\.(?:addPacket|withBundlePacket)\(\w+PacketTypes\.((?:CLIENTBOUND|SERVERBOUND)_\w+)/g)]
    .map(m => m[1]).filter(n => n.startsWith(direction));
  return Object.fromEntries(names.map((n, i) => [n.replace(direction + '_', ''), i]));
}

export async function connectPlayer(repo, port, username) {
  const protocol = await new Promise((resolve, reject) => {
    const probe = net.connect({host: '127.0.0.1', port});
    let response = Buffer.alloc(0);
    probe.on('error', reject);
    probe.once('connect', () => {
      const p = Buffer.alloc(2); p.writeUInt16BE(port);
      const handshake = Buffer.concat([vi(0), vi(-1), str('127.0.0.1'), p, vi(1)]);
      probe.write(Buffer.concat([vi(handshake.length), handshake, Buffer.from([1, 0])]));
    });
    probe.on('data', chunk => {
      response = Buffer.concat([response, chunk]);
      const size = readVi(response);
      if (!size || response.length < size.end + size.value) return;
      const id = readVi(response, size.end), len = readVi(response, id.end);
      resolve(JSON.parse(response.subarray(len.end, len.end + len.value)).version.protocol);
      probe.end();
    });
  });
  const gameIn = packetMap(repo, 'game/GameProtocols.java', 'CLIENTBOUND');
  const gameOut = packetMap(repo, 'game/GameProtocols.java', 'SERVERBOUND');
  const configIn = packetMap(repo, 'configuration/ConfigurationProtocols.java', 'CLIENTBOUND');
  const configOut = packetMap(repo, 'configuration/ConfigurationProtocols.java', 'SERVERBOUND');
  const loginIn = packetMap(repo, 'login/LoginProtocols.java', 'CLIENTBOUND');
  const loginOut = packetMap(repo, 'login/LoginProtocols.java', 'SERVERBOUND');
  const socket = net.connect({host: '127.0.0.1', port});
  let buffered = Buffer.alloc(0), state = 'login', compressed = false;
  const send = (id, data = Buffer.alloc(0)) => {
    if (process.env.SNAPSHOT_PROTOCOL_DEBUG) console.log('SEND', username, state, id, data.length);
    let body = Buffer.concat([vi(id), data]);
    if (compressed) body = Buffer.concat([vi(0), body]);
    socket.write(Buffer.concat([vi(body.length), body]));
  };
  const handshake = (version, intent) => {
    const p = Buffer.alloc(2); p.writeUInt16BE(port);
    send(0, Buffer.concat([vi(version), str('127.0.0.1'), p, vi(intent)]));
  };
  return new Promise((resolve, reject) => {
    socket.once('connect', () => {
      handshake(protocol, 2);
      send(loginOut.HELLO, Buffer.concat([str(username), Buffer.alloc(16)]));
    });
    socket.on('error', reject);
    socket.on('close', () => {
      console.log('CLIENT_CLOSED', username);
      if (state !== 'play') reject(Error('Client closed in ' + state));
    });
    socket.on('data', chunk => {
      try {
        buffered = Buffer.concat([buffered, chunk]);
        while (true) {
          const len = readVi(buffered);
          if (!len || buffered.length < len.end + len.value) break;
          let packet = buffered.subarray(len.end, len.end + len.value);
          buffered = buffered.subarray(len.end + len.value);
          if (compressed) { const size = readVi(packet); packet = size.value ? inflateSync(packet.subarray(size.end)) : packet.subarray(size.end); }
          const id = readVi(packet), data = packet.subarray(id.end);
          if (process.env.SNAPSHOT_PROTOCOL_DEBUG && state !== 'play') console.log('PACKET', username, state, id.value, data.length);
          if (state === 'login') {
            if (id.value === loginIn.LOGIN_COMPRESSION) compressed = true;
            else if (id.value === loginIn.LOGIN_FINISHED) { send(loginOut.LOGIN_ACKNOWLEDGED); state = 'config'; }
            else if (id.value === loginIn.LOGIN_DISCONNECT) throw Error('Login rejected: ' + data.toString());
          } else if (state === 'config') {
            if (id.value === configIn.SELECT_KNOWN_PACKS) send(configOut.SELECT_KNOWN_PACKS, vi(0));
            else if (id.value === configIn.FINISH_CONFIGURATION) { send(configOut.FINISH_CONFIGURATION); state = 'play'; console.log('CLIENT_PLAY', username); resolve(username); }
            else if (id.value === configIn.KEEP_ALIVE) send(configOut.KEEP_ALIVE, data);
            else if (id.value === configIn.PING) send(configOut.PONG, data);
            else if (id.value === configIn.DISCONNECT) throw Error('Configuration disconnected');
          } else if (state === 'play') {
            if (id.value === gameIn.KEEP_ALIVE) send(gameOut.KEEP_ALIVE, data);
            else if (id.value === gameIn.PING) send(gameOut.PONG, data);
            else if (id.value === gameIn.PLAYER_POSITION) send(gameOut.ACCEPT_TELEPORTATION, vi(readVi(data).value));
          }
        }
      } catch (error) { reject(error); console.error(error); }
    });
  });
}
