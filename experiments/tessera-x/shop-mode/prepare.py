from pathlib import Path
import os,subprocess,zipfile,hashlib,json,sys,shutil
R=Path(__file__).parent;P=R.parents[1]
J=Path(os.environ.get('TESSERA_SHOP_JAVA_HOME','C:/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot'))/'bin'
V=Path(os.environ.get('TESSERA_SHOP_VANILLA',str(Path.home()/'.gradle/caches/fabric-loom/26.3/minecraft-server.jar')))
T=Path(os.environ.get('TESSERA_SHOP_BUNDLER',str(P/'source/folia-server/build/libs/folia-bundler-26.3.build.019-beta.jar')))
A=R/('artifacts-release' if '--release' in sys.argv else 'artifacts-final' if '--final' in sys.argv else 'artifacts-v6' if '--v6' in sys.argv else 'artifacts-v5' if '--v5' in sys.argv else 'artifacts-v4' if '--v4' in sys.argv else 'artifacts-v3' if '--v3' in sys.argv else 'artifacts-v2' if '--v2' in sys.argv else 'artifacts');A.mkdir(exist_ok=True)
previous=Path(json.loads((R/'runtime-artifacts.json').read_text())['directory']) if (R/'runtime-artifacts.json').exists() else R/'artifacts'
for n in ['deps-vanilla','deps-tessera','classes','patcher','plugin','artifacts','runs','temp']:(R/n).mkdir(exist_ok=True)
os.environ['TEMP']=os.environ['TMP']=str(R/'temp')
def sha(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def extract(bundle,dest):
 with zipfile.ZipFile(bundle) as z:
  result=None
  for e in z.infolist():
   if e.filename.startswith('META-INF/') and e.filename.endswith('.jar'):
    p=dest/Path(e.filename).name;p.write_bytes(z.read(e))
    if e.filename.startswith('META-INF/versions/'):result=(e.filename,p)
  return result
ve,vc=extract(V,R/'deps-vanilla');te,tc=extract(T,R/'deps-tessera')
def cp(p):return ';'.join(f.as_posix() for f in p.glob('*.jar'))
def compile(sources,libs,out,name):
 a=R/(name+'.args');a.write_text('\n'.join(['--release','25','-encoding','UTF-8','-cp','"'+libs+'"','-d','"'+out.as_posix()+'"']+['"'+s.as_posix()+'"' for s in sources]),encoding='utf-8')
 subprocess.run([str(J/'javac.exe'),'@'+str(a)],check=True)
compile([R/'src/shoplab/ShopLab.java'],cp(R/'deps-vanilla'),R/'classes','lab')
compile([R/'src/PatchShopLab.java'],cp(R/'deps-tessera'),R/'patcher','patcher')
compile([R/'src/ShopPlugin.java'],cp(R/'deps-tessera'),R/'plugin','plugin')
(R/'plugin/plugin.yml').write_text('name: ShopLiveLab\nversion: 1.0\nmain: shopplugin.ShopPlugin\napi-version: "26.3"\nfolia-supported: true\n')
subprocess.run([str(J/'jar.exe'),'--create','--file',str(A/'shop-lab-plugin.jar'),'-C',str(R/'plugin'),'.'],check=True)
for name,bundle,entry,core in [('vanilla',V,ve,vc),('tessera',T,te,tc)]:
 if name=='vanilla' and '--tessera-only' in sys.argv:continue
 patched=A/f'{name}-instrumented-core.jar'
 libs=str(R/'patcher')+';'+cp(R/'deps-tessera')+';'+cp(R/f'deps-{name}')
 subprocess.run([str(J/'java.exe'),'-cp',libs,'PatchShopLab',str(core),str(patched),str(R/'classes'),name],check=True)
 with zipfile.ZipFile(bundle) as z,zipfile.ZipFile(A/f'{name}-shop-lab.jar','w',zipfile.ZIP_DEFLATED,compresslevel=1) as out:
  for e in z.infolist():
   data=z.read(e)
   if e.filename==entry:data=patched.read_bytes()
   if e.filename=='META-INF/versions.list':
    data=('\n'.join('\t'.join([sha(patched)]+s.split('\t')[1:]) if entry.endswith(s.split('\t')[-1]) else s for s in data.decode().splitlines())+'\n').encode()
   out.writestr(e.filename,data)
assert hashlib.file_digest(V.open('rb'),'sha1').hexdigest()=='33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c','Vanilla 26.3 differs from verified official Mojang server SHA-1'
(R/'manifest.json').write_text(json.dumps({'vanillaSha256':sha(V),'tesseraSha256':sha(T),'scope':'Normal autonomous ticks; no replacement Brain/entity ticks, clocks or RNG. Lab observers and scene input only. No production contact.','sources':{str(f.relative_to(R)):sha(f) for f in (R/'src').rglob('*.java')}},indent=2))
(A/'manifest.json').write_bytes((R/'manifest.json').read_bytes())
if '--tessera-only' in sys.argv and not (A/'vanilla-shop-lab.jar').exists():shutil.copyfile(previous/'vanilla-shop-lab.jar',A/'vanilla-shop-lab.jar')
(R/'runtime-artifacts.json').write_text(json.dumps({'directory':str(A)}))
print('Lab artifacts prepared.')
