"""Authoritative schema source; regeneration is deterministic and never touches game assets."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
ID = r"^[a-z][a-z0-9_-]*:[a-z0-9_]+(?:[/.-][a-z0-9_]+)*$"
LOCAL = r"^[A-Za-z][A-Za-z0-9_]*$"
REF = r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*$"
PACK = r"^(?!(?:core|gt|fluid|name)$)[a-z][a-z0-9_-]*$"
PRE = r"(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
VERSION = r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-" + PRE + r"(?:\." + PRE + r")*)?$"
def string(pattern=None, limit=256):
    result = {"type": "string", "minLength": 1, "maxLength": limit}
    if pattern: result["pattern"] = pattern
    return result
def obj(properties, required=()):
    return {"type": "object", "properties": properties, "required": list(required), "additionalProperties": False}
def array(items, minimum=0, maximum=16384):
    return {"type": "array", "items": items, "minItems": minimum, "maxItems": maximum}
def enum(*values): return {"enum": list(values)}
def ref(name): return {"$ref": "#/$defs/" + name}
def integer(low=0, high=2147483647): return {"type": "integer", "minimum": low, "maximum": high}
def schemas():
    coordinate = {"type": "array", "prefixItems": [{"type": "number", "minimum": -10000, "maximum": 10000}]*2, "items": False, "minItems": 2, "maxItems": 2}
    defs = {"id": string(ID), "local": string(LOCAL,64), "atomRef": string(REF), "position": coordinate,
            "color": string(r"^#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$",9)}
    defs["override"] = obj({"pack": string(PACK,64), "id": ref("id")}, ["pack","id"])
    common = {"schemaVersion": {"const":1}, "id": ref("id"), "overrides": ref("override")}
    label = obj({"text": {"type":"string","maxLength":128}, "element": string(limit=32), "color": ref("color")},["text"])
    atom = {"id":ref("local"), "position":ref("position"), "element":{"type":["string","null"],"minLength":1,"maxLength":32},
            "label":{"type":"string","maxLength":128},"labelMode":enum("symbol","skeletal","hidden"),
            "hydrogens":integer(0,32),"isotope":integer(1,999),"charge":integer(-16,16),"color":ref("color"),
            "labels":obj({key:label for key in ["above","right","below","left"]})}
    defs["atom"] = obj(atom,["id","position","element"])
    defs["bond"] = obj({"from":ref("atomRef"),"to":ref("atomRef"),"order":enum(1,2,3),"centered":{"type":"boolean"},
                        "style":enum("solid","dotted","thick","inward","outward"),
                        "strokes":array(enum("solid","dotted","thick","inward","outward"),1,8)},["from","to"])
    defs["bond"]["oneOf"] = [{"required":["order"],"not":{"required":["strokes"]}}, {"required":["strokes"],"not":{"anyOf":[{"required":["order"]},{"required":["style"]}]}}]
    defs["bond"]["allOf"] = [{"if":{"properties":{"style":enum("inward","outward")},"required":["style"]},"then":{"properties":{"order":{"const":1}}}}]
    transform = {"id":ref("local"),"origin":ref("position"),"rotation":{"type":"number","minimum":-360000,"maximum":360000},
                 "mirror":enum("none","x","y"),"scale":{"type":"number","exclusiveMinimum":0,"maximum":100},
                 "atomMap":{"type":"object","propertyNames":ref("atomRef"),"additionalProperties":ref("atomRef"),"maxProperties":16384}}
    benzene = obj({**transform,"module":{"const":"core:benzene"},"parameters":obj({"bondLength":{"type":"number","exclusiveMinimum":0,"maximum":100},"doubleBondStart":enum(0,1)})},["id","module"])
    ring = obj({**transform,"module":{"const":"core:ring"},"parameters":obj({"elements":array(string(limit=32),3,64),"bondOrders":array(enum(1,2,3),3,64),"bondLength":{"type":"number","exclusiveMinimum":0,"maximum":100}},["elements","bondOrders"])},["id","module","parameters"])
    fragment = obj({**transform,"fragment":ref("id")},["id","fragment"])
    defs["instance"] = {"oneOf":[benzene,ring,fragment]}
    defs["annotation"] = {"oneOf":[
        obj({"type":{"const":"bracket"},"atoms":array(ref("atomRef"),1),"sub":{"type":"string","maxLength":128},"sup":{"type":"string","maxLength":128},"color":ref("color")},["type","atoms"]),
        obj({"type":{"const":"text"},"position":ref("position"),"text":string(limit=128),"color":ref("color")},["type","position","text"]),
        obj({"type":{"const":"ellipse"},"atoms":array(ref("atomRef"),1),"matrix":{"type":"array","prefixItems":[{"type":"number","minimum":-100,"maximum":100}]*4,"items":False,"minItems":4,"maxItems":4},"color":ref("color")},["type","atoms","matrix"])
    ]}
    geometry = {**common,"atoms":array(ref("atom")),"bonds":array(ref("bond")),"instances":array(ref("instance"),0,4096),
                "annotations":array(ref("annotation")),"ports":{"type":"object","propertyNames":ref("local"),"additionalProperties":ref("atomRef"),"maxProperties":4096}}
    for kind in ["molecule","fragment"]:
        defs[kind] = obj({**geometry,"type":{"const":kind}},["schemaVersion","id","type"])
        defs[kind]["anyOf"] = [{"required":[key],"properties":{key:{"minItems":1}}} for key in ["atoms","instances"]]
    amount = {"oneOf":[{"type":"integer","exclusiveMinimum":0},string(r"^[1-9][0-9]*(?:/[1-9][0-9]*)?$",4096)]}
    material_ref = {"anyOf":[ref("id"),string(r"^gt:.+$")]}
    component = obj({"material":material_ref,"amount":amount},["material","amount"])
    composition = {"oneOf":[obj({"source":enum("gregtech","formula")},["source"]),obj({"source":{"const":"explicit"},"basis":enum("mixture_parts","stoichiometric"),"components":array(component,1,4096)},["source","basis","components"])]}
    defs["material"] = obj({**common,"type":{"const":"material"},"bindings":{**array(string(r"^(?:gt|fluid|name):.+$"),1,4096),"uniqueItems":True},
                            "formula":string(limit=4096),"structure":ref("id"),"composition":composition,
                            "display":obj({"mode":enum("auto","molecule","composition","text")},["mode"]),"disabled":{"type":"boolean"}},["schemaVersion","id","type","bindings"])
    defs["material"]["allOf"] = [{"if":{"properties":{"disabled":{"const":True}},"required":["disabled"]},"then":{"not":{"anyOf":[{"required":[x]} for x in ["formula","structure","composition","display"]]}}}]
    rules = [obj({"kind":{"const":"alias"},"from":string(),"to":ref("id")},["kind","from","to"]),
             obj({"kind":{"const":"strip_prefix"},"prefixes":array(string(limit=64),1,64),"target":enum("structure","composition")},["kind","prefixes","target"]),
             obj({"kind":{"const":"composition_candidate"},"materials":{**array(string(r"^gt:.+$"),0,4096),"uniqueItems":True},"containsAny":{**array(string(limit=64),0,64),"uniqueItems":True}},["kind"]),
             obj({"kind":{"const":"element_binding"},"symbol":string(limit=32),"material":string(r"^gt:.+$")},["kind","symbol","material"])]
    rules[2]["anyOf"] = [{"required":[x],"properties":{x:{"minItems":1}}} for x in ["materials","containsAny"]]
    defs["bindings"] = obj({**common,"type":{"const":"bindings"},"rules":array({"oneOf":rules},1,4096)},["schemaVersion","id","type","rules"])
    definition = {"$schema":"https://json-schema.org/draft/2020-12/schema","$id":"urn:gtmoldraw:definition:1","title":"GTMolDraw data definition v1","oneOf":[ref(x) for x in ["material","molecule","fragment","bindings"]],"$defs":defs}
    manifest = obj({"schemaVersion":{"const":1},"type":{"const":"pack"},"id":string(PACK,64),"version":string(VERSION,128),"description":{"type":"string","maxLength":2048},
                    "modVersion":obj({"min":string(VERSION,128),"maxExclusive":string(VERSION,128)},["min","maxExclusive"]),
                    "dependencies":array(obj({"id":string(PACK,64),"version":string(VERSION,128)},["id","version"]),0,64),
                    "providers":{**array(enum("gregtech"),0,1),"uniqueItems":True}},["schemaVersion","type","id","version","modVersion","dependencies","providers"])
    manifest.update({"$schema":"https://json-schema.org/draft/2020-12/schema","$id":"urn:gtmoldraw:pack:1","title":"GTMolDraw pack manifest v1"})
    return {"definition.schema.json":definition,"pack.schema.json":manifest}
if __name__ == "__main__":
    for name, data in schemas().items():
        (ROOT/"schema"/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
