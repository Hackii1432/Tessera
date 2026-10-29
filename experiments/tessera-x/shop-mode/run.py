from pathlib import Path
import json,os,shutil,subprocess,sys,time
R=Path(__file__).parent;mode=sys.argv[1];label=sys.argv[2] if len(sys.argv)>2 else str(int(time.time()))
while (R/'build-in-progress.flag').exists():time.sleep(.5)
assert mode in ['vanilla','baseline','shop']
A=Path(json.loads((R/'runtime-artifacts.json').read_text())['directory']) if (R/'runtime-artifacts.json').exists() else R/'artifacts'
run=R/'runs'/f'{label}-{mode}';run.mkdir(parents=True,exist_ok=False);(run/'plugins').mkdir();(run/'config').mkdir()
(run/'eula.txt').write_text('eula=true\n')
(run/'server.properties').write_text('\n'.join(['server-ip=127.0.0.1','server-port=0','online-mode=false','enable-rcon=false','view-distance=2','simulation-distance=2','spawn-protection=0','level-type=minecraft:flat','level-seed=829104','generate-structures=false','generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}','sync-chunk-writes=false','pause-when-empty-seconds=-1','max-tick-time=0','spawn-monsters=false'])+'\n')
if mode!='vanilla':
 (run/'spigot.yml').write_text('world-settings:\n  default:\n    entity-activation-range:\n      villagers: 0\n')
 shutil.copyfile(A/'shop-lab-plugin.jar',run/'plugins/shop-lab-plugin.jar')
 (run/'config/paper-global.yml').write_text('_version: 31\nthreaded-regions:\n  threads: 4\n  grid-exponent: 4\nchunk-system:\n  worker-threads: 2\n  io-threads: 1\nspark:\n  enabled: false\nupdate-checker:\n  enabled: false\n')
 (run/'config/tessera-x.yml').write_text('experimental-options:\n  enabled: '+str(mode=='shop').lower()+'\n  automatic-shop: '+str(mode=='shop').lower()+'\n')
kind='vanilla' if mode=='vanilla' else 'tessera'
java=Path(os.environ.get('TESSERA_SHOP_JAVA_HOME','C:/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot'))/'bin/java.exe'
args=[str(java),'-Xms1G','-Xmx5G','-XX:+UseG1GC','-XX:ActiveProcessorCount=8','-Dfile.encoding=UTF-8',f'-Dshoplab.mode={mode}']
if '--smoke' in sys.argv:args+=['-Dshoplab.smoke=true']
if '--bench' in sys.argv:args+=['-Dshoplab.benchOnly=true']
args+=['-jar',str(A/f'{kind}-shop-lab.jar'),'nogui']
if (A/'manifest.json').exists():shutil.copyfile(A/'manifest.json',run/'lab-manifest.json')
env=os.environ.copy();env['TEMP']=env['TMP']=str(R/'temp');start=time.time()
with (run/'stdout.log').open('w',encoding='utf-8') as out,(run/'stderr.log').open('w',encoding='utf-8') as err:
 p=subprocess.Popen(args,cwd=run,env=env,stdin=subprocess.PIPE,stdout=out,stderr=err,text=True)
 (R/'active-run.json').write_text(json.dumps({'pid':p.pid,'run':str(run),'args':args},indent=2))
 try:
  while p.poll() is None:
   if time.time()-start>1800:raise TimeoutError('Local shop lab timeout')
   time.sleep(2)
 finally:
  if p.poll() is None:
   p.stdin.write('stop\n');p.stdin.flush()
   try:p.wait(timeout=45)
   except subprocess.TimeoutExpired:p.kill();p.wait()
data={'mode':mode,'exitCode':p.returncode,'seconds':time.time()-start,'productionContacted':False,'allProcessesStopped':p.poll() is not None}
(run/'invocation.json').write_text(json.dumps(data,indent=2));print(json.dumps(data))
if not (run/'shop-result.json').exists():
 print((run/'stderr.log').read_text(encoding='utf-8')[-5000:]);print((run/'stdout.log').read_text(encoding='utf-8')[-6000:]);raise SystemExit('No lab result')
result=json.loads((run/'shop-result.json').read_text());print(result['status'],str(run/'shop-result.json'))
if result['status']!='PASS':print(result.get('failure'));raise SystemExit(1)
if p.returncode:raise SystemExit(p.returncode)
