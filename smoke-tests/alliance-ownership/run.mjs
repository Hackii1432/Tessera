import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {connectPlayer} from '../runtime-snapshot/client.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const [java, input] = process.argv.slice(2);
if (!java || !input) throw Error('Usage: node run.mjs <java25> <server.jar>');
const jar = path.resolve(input);
const sha256 = createHash('sha256').update(readFileSync(jar)).digest('hex');
const work = path.join(repo, 'build', `native-alliance-${Date.now()}`);
mkdirSync(path.join(work, 'plugins'), {recursive: true});
mkdirSync(path.join(work, 'config'));
writeFileSync(path.join(work, '.tessera-disposable-fixture'), 'Native alliance tests, isolated and disposable. No production data.\n');
writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
const reserve = net.createServer();
await new Promise((resolve, reject) => { reserve.once('error', reject); reserve.listen(0, '127.0.0.1', resolve); });
const port = reserve.address().port;
await new Promise(resolve => reserve.close(resolve));
const flat = JSON.stringify({biome:'minecraft:plains',lakes:false,features:false,structure_overrides:[],
  layers:[{block:'minecraft:bedrock',height:1},{block:'minecraft:dirt',height:2},{block:'minecraft:grass_block',height:1}]});
writeFileSync(path.join(work, 'server.properties'), `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nenforce-secure-profile=false\nwhite-list=false\ngamemode=survival\nnetwork-compression-threshold=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerator-settings=${flat}\nallow-flight=true\n`);
writeFileSync(path.join(work, 'config/paper-global.yml'), '_version: 31\nthreaded-regions:\n  threads: 4\n');
copyFileSync(path.join(repo, 'test-plugin/build/libs/tessera-runtime-world-smoke.jar'), path.join(work, 'plugins/smoke.jar'));
const vanilla = path.join(repo, 'folia-server/.gradle/caches/paperweight/taskCache/downloadServerJar.jar');
if (existsSync(vanilla) && createHash('sha256').update(readFileSync(vanilla)).digest('hex') === 'd052f14d7a173734fba553711e5b570162e2f2a313267ee31a21b975a679be64') {
  mkdirSync(path.join(work, 'cache'));
  copyFileSync(vanilla, path.join(work, 'cache/mojang_26.3.jar'));
}
console.log('ALLIANCE_FIXTURE', work, 'PORT', port, 'SHA256', sha256);
const child = spawn(java, ['-XX:ActiveProcessorCount=4', '-Xms512M', '-Xmx3G', '-jar', jar, '--nogui'],
  {cwd:work,windowsHide:true,env:{...process.env,TESSERA_SMOKE_MODE:'alliance-ownership'},stdio:['pipe','pipe','pipe']});
let log='', started=false, clientFailure=null, timedOut=false;
const observations=[];
function output(chunk) {
  log += chunk.toString(); process.stdout.write(chunk);
  if (!started && log.includes('ALLIANCE_READY_CLIENTS') && log.includes('For help, type "help"')) {
    started=true;
    Promise.all(['AllianceAlpha','AllianceBeta'].map(name => connectPlayer(repo, port, name,
      event => { if (!log.includes('ALLIANCE_PASS')) observations.push(event); })))
      .catch(error => { clientFailure=String(error); child.stdin.write('stop\n'); });
  }
}
child.stdout.on('data', output); child.stderr.on('data', output);
const timeout=setTimeout(() => { timedOut=true; child.stdin.write('stop\n'); },300000);
const kill=setTimeout(() => { if (child.exitCode===null) child.kill(); },345000);
const code=await new Promise((resolve,reject) => { child.once('exit',resolve); child.once('error',reject); });
clearTimeout(timeout); clearTimeout(kill);
const checks=existsSync(path.join(work,'alliance-checks.txt')) ? readFileSync(path.join(work,'alliance-checks.txt'),'utf8') : '';
const joins=observations.filter(e=>e.type==='login').length;
const kicks=observations.filter(e=>e.type==='kicked').length;
const disconnects=observations.filter(e=>e.type==='end').length;
const required=['two distinct native regions','natural Warden darkness AllianceAlpha','natural Warden darkness AllianceBeta',
  'natural ElderGuardian fatigue AllianceAlpha','natural ElderGuardian fatigue AllianceBeta',
  'first region mobs survive natural aura ticks','second region mobs survive natural aura ticks',
  'stale Creaking memory rejected remote region','stale Creaking memory rejected other dimension',
  'stale retaliation attacker rejected remote region','stale retaliation attacker rejected other dimension',
  'remote anger UUID retained remote region','remote anger UUID retained other dimension',
  'fangs native magic damage with remote owner remote region','fangs native magic damage with remote owner other dimension',
  'effect source retirement stops old-owner application','potion target retirement vetoes mutation',
  'retired target effect map unchanged','alliance callback retirement stops reverse evaluation',
  'native sword sweep damages local target beside remote-owned pets','native mace knockback beside remote-owned pets',
  'local retaliation still uses vanilla selection','local retaliation target retained',
  'native piglin group alarm retains local target','piglin alliance callback retirement stops group mutation',
  'Warden anger resolves again after owner return',
  'ElderGuardian appearance veto still suppresses fatigue','push callback retirement leaves velocity unchanged',
  'Creaking source callback stops movement data continuation',
  'local Vex root owner uses vanilla binding','remote Vex root owner unavailable remote region',
  'remote Vex root owner unavailable other dimension','Evoker and Vex alliance evaluation survives remote region',
  'Evoker and Vex alliance evaluation survives other dimension','Vex root owner resumes after caster return',
  'clients remained connected after repeated transfers'];
for (const type of ['WOLF','CAT','PARROT','NAUTILUS']) required.push(`owner alliance resumes ${type}`,`owner UUID retained after return ${type}`);
const passed=code===0&&!timedOut&&clientFailure===null&&log.includes('ALLIANCE_PASS')&&joins===2&&kicks===0&&disconnects===0
  &&required.every(check=>checks.includes(check))&&!/ALLIANCE_FAIL|Thread failed main thread check|Entity threw exception|failed to tick:|ConcurrentModificationException/.test(log);
writeFileSync(path.join(work,'runner.log'),log);
writeFileSync(path.join(work,'result.json'),JSON.stringify({jar,sha256,work,port,code,passed,timedOut,clientFailure,joins,kicks,disconnects,checks,observations},null,2));
console.log('ALLIANCE_RESULT',passed,path.join(work,'result.json'));
if (!passed) process.exitCode=1;
