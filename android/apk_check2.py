import zipfile, sys
base = r'D:\files\Desk\work\tank-github\rac-hide-main\android\app\build'
z = zipfile.ZipFile(base + '\\Tuyin-1.0.apk')
files = [i.filename for i in z.infolist()]
sys.stdout.write('dex=%s size=%d\n' % (
    any(f.endswith('classes.dex') for f in files),
    sum(i.file_size for i in z.infolist())))
for i in z.infolist():
    sys.stdout.write('  %s %d\n' % (i.filename, i.file_size))
