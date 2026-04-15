import os
import re

tiny_path = r"C:\Users\frand\.gradle\caches\fabric-loom\1.21.10\loom.mappings.1_21_10.layered+hash.2198-v2\mappings.tiny"

fqcn_map = {}
simple_map = {}
field_map = {}
method_map = {}

def process_tiny():
    with open(tiny_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip('\n')
            if line.startswith('c\t'):
                # c	official	intermediary	named
                parts = line.split('\t')
                if len(parts) >= 4:
                    intermediary = parts[2]
                    named = parts[3]
                    
                    # FQCN maps
                    int_fqcn = intermediary.replace('/', '.').replace('$', '.')
                    named_fqcn = named.replace('/', '.').replace('$', '.')
                    if int_fqcn.startswith("net.minecraft"):
                        fqcn_map[int_fqcn] = named_fqcn
                    
                    # Simple names
                    int_simple = intermediary.split('/')[-1].split('$')[-1]
                    named_simple = named.split('/')[-1].split('$')[-1]
                    simple_map[int_simple] = named_simple

            elif line.startswith('\tf\t') or line.startswith('\tm\t'):
                parts = line.split('\t')
                if len(parts) >= 6:
                    t = parts[1]
                    intermediary_member = parts[4]
                    named_member = parts[5]
                    
                    if t == 'f':
                        field_map[intermediary_member] = named_member
                    elif t == 'm':
                        method_map[intermediary_member] = named_member

def remap_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Iterate over fqcn to replace imports and fully qualified usages
    # Sort by length descending to replace longer FQCNs first (e.g. inner classes before outer)
    for int_fqcn in sorted(fqcn_map.keys(), key=len, reverse=True):
        if int_fqcn in content:
            content = content.replace(int_fqcn, fqcn_map[int_fqcn])

    # Now replace simple names, methods, and fields
    # We use regex \b to ensure we only replace whole words
    # class_XXXX
    content = re.sub(r'\bclass_\d+\b', lambda m: simple_map.get(m.group(0), m.group(0)), content)
    # field_XXXX
    content = re.sub(r'\bfield_\d+\b', lambda m: field_map.get(m.group(0), m.group(0)), content)
    # method_XXXX
    content = re.sub(r'\bmethod_\d+\b', lambda m: method_map.get(m.group(0), m.group(0)), content)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

def main():
    print("Parsing tiny mappings...")
    process_tiny()
    print(f"Loaded {len(fqcn_map)} class mappings, {len(field_map)} field mappings, {len(method_map)} method mappings.")
    
    dirs_to_process = [
        r"C:\Users\frand\Documents\bomboaddons-1.21.10\src\client\java",
        r"C:\Users\frand\Documents\bomboaddons-1.21.10\src\main\java"
    ]
    
    for d in dirs_to_process:
        for root, dirs, files in os.walk(d):
            for file in files:
                if file.endswith(".java"):
                    filepath = os.path.join(root, file)
                    remap_file(filepath)
    print("Done remapping!")

if __name__ == '__main__':
    main()
