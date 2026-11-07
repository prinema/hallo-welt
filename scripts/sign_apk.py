"""Sign the tested build with the same PRIVATE key for every update."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

p=argparse.ArgumentParser()
for name in ('input','signer','keystore','password-file','output'):
    p.add_argument('--'+name,required=True,type=Path)
a=p.parse_args()
java=Path(shutil.which('java')).resolve()
env=os.environ.copy()
lib=java.parent.parent/'lib'
if (lib/'libjli.so').exists():env['LD_LIBRARY_PATH']=str(lib)+(':'+env['LD_LIBRARY_PATH'] if env.get('LD_LIBRARY_PATH') else '')
base=[str(java),'-jar',str(a.signer)]
subprocess.run(base+['sign','--ks',str(a.keystore),'--ks-key-alias','tagebuch','--ks-pass','file:'+str(a.password_file),'--key-pass','file:'+str(a.password_file),'--out',str(a.output),str(a.input)],check=True,env=env)
subprocess.run(base+['verify','--verbose','--print-certs',str(a.output)],check=True,env=env)
