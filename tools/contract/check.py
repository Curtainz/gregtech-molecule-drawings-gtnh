"""Stage-1 authoring checks. Not a runtime loader, assembler, formatter or migration tool."""
import copy
import json
from pathlib import Path
from jsonschema import Draft202012Validator
from emit_schema import ROOT, schemas


def parse_jsonc(text):
    chars=list(text);i=0;quoted=False;escaped=False
    while i<len(chars):
        c=chars[i]
        if quoted:
            if escaped:escaped=False
            elif c=='\\':escaped=True
            elif c=='"':quoted=False
            i+=1;continue
        if c=='"':quoted=True;i+=1;continue
        if c=='/' and i+1<len(chars) and chars[i+1]=='/':
            while i<len(chars) and chars[i] not in '\r\n':chars[i]=' ';i+=1
            continue
        if c=='/' and i+1<len(chars) and chars[i+1]=='*':
            start=i;chars[i]=chars[i+1]=' ';i+=2
            while i+1<len(chars) and not(chars[i]=='*' and chars[i+1]=='/'):
                if chars[i] not in '\r\n':chars[i]=' '
                i+=1
            if i+1>=len(chars):raise ValueError('Unclosed comment at offset '+str(start))
            chars[i]=chars[i+1]=' ';i+=2;continue
        i+=1
    # Remove only a comma immediately preceding a closing delimiter, outside strings.
    quoted=False;escaped=False
    for i,c in enumerate(chars):
        if quoted:
            if escaped:escaped=False
            elif c=='\\':escaped=True
            elif c=='"':quoted=False
        elif c=='"':quoted=True
        elif c==',':
            j=i+1
            while j<len(chars) and chars[j].isspace():j+=1
            if j<len(chars) and chars[j] in ']}':chars[i]=' '
    def pairs(items):
        result={}
        for key,value in items:
            if key in result:raise ValueError('Duplicate property: '+key)
            result[key]=value
        return result
    def invalid_constant(value):raise ValueError('Non-JSON number: '+value)
    return json.loads(''.join(chars),object_pairs_hook=pairs,parse_constant=invalid_constant)


def validate_review():
    validators={}
    for name,expected in schemas().items():
        actual=json.loads((ROOT/'schema'/name).read_text(encoding='utf-8'))
        if actual!=expected:raise ValueError('Schema source/output drift: '+name)
        Draft202012Validator.check_schema(actual)
        validators[name]=Draft202012Validator(actual)
    packs={};records=[];paths={}
    for path in sorted((ROOT/'data-packs'/'review').rglob('*.jsonc')):
        try:
            data=parse_jsonc(path.read_text(encoding='utf-8'))
            kind='pack.schema.json' if path.name=='pack.jsonc' else 'definition.schema.json'
            validators[kind].validate(data)
            owner=path.relative_to(ROOT/'data-packs'/'review').parts[0]
            if kind=='pack.schema.json':
                if owner!=data['id'] or owner in packs:raise ValueError('Pack ID/directory conflict')
                packs[owner]=data
            else:records.append((owner,data));paths[id(data)]=path
        except Exception as error:raise ValueError(f'{path.relative_to(ROOT)}: {error}') from error
    definitions={}
    for owner,data in records:
        if owner not in packs:raise ValueError('Missing manifest for '+owner)
        override=data.get('overrides')
        if override:
            if override['id']!=data['id']:raise ValueError('Override must retain canonical ID')
            if override['pack'] not in [d['id'] for d in packs[owner]['dependencies']]:raise ValueError('Override missing direct dependency')
        elif data['id'].split(':',1)[0]!=owner:raise ValueError('Definition namespace is not owned by pack')
        key=(owner,data['id'])
        if key in definitions:raise ValueError('Duplicate definition '+str(key))
        definitions[key]=data
    for owner,pack in packs.items():
        for dependency in pack['dependencies']:
            if dependency['id'] not in packs or packs[dependency['id']]['version']!=dependency['version']:raise ValueError('Dependency mismatch')
    for owner,data in records:
        if 'overrides' in data:
            target=definitions[(data['overrides']['pack'],data['id'])]
            if target['type']!=data['type']:raise ValueError('Override changed definition kind')
        refs=[]
        if 'structure' in data:refs.append((data['structure'],'molecule'))
        for instance in data.get('instances',[]):
            if 'fragment' in instance:refs.append((instance['fragment'],'fragment'))
        for rule in data.get('rules',[]):
            if rule['kind']=='alias':refs.append((rule['to'],'molecule'))
        for target,kind in refs:
            candidates=[d for _,d in records if d['id']==target and 'overrides' not in d]
            if len(candidates)!=1 or candidates[0]['type']!=kind:raise ValueError('Invalid reference '+target)
    definition_validator=validators['definition.schema.json']
    by_id={d['id']:d for _,d in records if 'overrides' not in d}
    negative=[]
    water=by_id['official:water_structure'];mix=by_id['official:review_alloy'];ring=by_id['official:benzene_structure']
    bad=copy.deepcopy(water);bad['unexpected']=True;negative.append(bad)
    bad=copy.deepcopy(water);bad['atoms'][0]['position']=[0];negative.append(bad)
    bad=copy.deepcopy(water);bad['bonds'][0]['order']=4;negative.append(bad)
    bad=copy.deepcopy(water);bad['bonds'][0]['strokes']=['solid'];negative.append(bad)
    bad=copy.deepcopy(water);bad['bonds'][0]['order']=2;bad['bonds'][0]['style']='inward';negative.append(bad)
    bad=copy.deepcopy(mix);bad['composition']['components'][0]['amount']=0;negative.append(bad)
    bad=copy.deepcopy(mix);bad['composition']['components'][0]['amount']=1.5;negative.append(bad)
    bad=copy.deepcopy(mix);bad['composition']['components'][0]['amount']='1/0';negative.append(bad)
    bad=copy.deepcopy(ring);bad['instances'][0]['atomMap']={'a3':'bad..id'};negative.append(bad)
    bad=copy.deepcopy(ring);bad['instances'][0]['module']='core:execute';negative.append(bad)
    bad=copy.deepcopy(by_id['official:water']);bad['disabled']=True;negative.append(bad)
    bad=copy.deepcopy(water);bad['schemaVersion']=2;negative.append(bad)
    for bad in negative:
        if definition_validator.is_valid(bad):raise AssertionError('Invalid fixture accepted: '+repr(bad))
    assert parse_jsonc('{"s":"// not a comment",/* block */"a":[1,2,],}')=={'s':'// not a comment','a':[1,2]}
    for invalid in ['{"a":1,"a":2}',"{'a':1}",'{a:1}','{"n":NaN}','/* unclosed','/*','[1,,]']:
        try:parse_jsonc(invalid)
        except (ValueError,json.JSONDecodeError):pass
        else:raise AssertionError('Non-JSONC accepted: '+invalid)
    # Check the declared fusion fixture numerically against the documented benzene ABI.
    import math
    fused=by_id['official:naphthalene_structure']['instances'];left,right=fused
    vertices=[(math.cos(i*math.pi/3),math.sin(i*math.pi/3)) for i in range(6)]
    for local,target in right['atomMap'].items():
        local_index=int(local[1:]);target_index=int(target.split('.a')[1])
        p=(vertices[local_index][0]+right['origin'][0],vertices[local_index][1]+right['origin'][1])
        assert math.dist(p,vertices[target_index])<1e-6,'Fusion positions disagree'
    assert (0+left['parameters']['doubleBondStart'])%2==(3+right['parameters']['doubleBondStart'])%2,'Shared edge order disagrees'
    edges={};atom_ids=set()
    for instance in fused:
        def canonical(index):
            local='a'+str(index)
            return instance.get('atomMap',{}).get(local,instance['id']+'.'+local)
        for index in range(6):
            atom_ids.add(canonical(index))
            key=tuple(sorted([canonical(index),canonical((index+1)%6)]))
            order=2 if (index+instance['parameters']['doubleBondStart'])%2==0 else 1
            if key in edges:assert edges[key]==order,'Fusion bond conflict'
            edges[key]=order
    assert len(atom_ids)==10 and len(edges)==11 and list(edges.values()).count(2)==5,'Unexpected naphthalene fixture topology'
    pack_validator=validators['pack.schema.json']
    for reserved in ['core','gt','fluid','name']:
        bad=copy.deepcopy(packs['official']);bad['id']=reserved
        assert not pack_validator.is_valid(bad),'Reserved provider namespace accepted'
    bad=copy.deepcopy(packs['official']);bad['version']='1.0.0-01'
    assert not pack_validator.is_valid(bad),'Invalid numeric prerelease accepted'
    print(f'Stage-1 contract checks passed: 2 schemas, {len(packs)} manifests, {len(records)} definitions, {len(negative)} schema rejection cases, JSONC lexical cases and fusion fixture.')
    print('Scope: authoring contract checks only; runtime expansion/reload is not implemented in this stage.')

if __name__=='__main__':validate_review()
