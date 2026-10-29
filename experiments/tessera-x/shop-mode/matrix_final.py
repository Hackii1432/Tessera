from pathlib import Path
import subprocess,sys
R=Path(__file__).parent
for mode,label,extra in [('baseline','final-b1',[]),('shop','final-s1',[]),('shop','final-s2',['--bench']),('baseline','final-b2',['--bench']),('vanilla','final-v2',[]),('vanilla','final-v3',['--bench'])]:
 print('START',label,mode,flush=True)
 subprocess.run([sys.executable,str(R/'run.py'),mode,label]+extra,check=True)
 print('DONE',label,mode,flush=True)
