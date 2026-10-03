#!/usr/bin/env python3
"""Create reviewable inventory without huge scanner cache or package files."""
import collections,json,sys
report=json.load(open(sys.argv[1]))
output={'artifact':report.get('ArtifactName'),'createdAt':report.get('CreatedAt'),'metadata':{'imageID':report.get('Metadata',{}).get('ImageID')},'results':[]}
for result in report.get('Results',[]):
 vulns=result.get('Vulnerabilities',[])
 output['results'].append({'target':result['Target'],'type':result.get('Type'),'counts':dict(collections.Counter(v['Severity'] for v in vulns)),'findings':[{'id':v['VulnerabilityID'],'package':v['PkgName'],'installed':v['InstalledVersion'],'fixed':v.get('FixedVersion'),'severity':v['Severity'],'url':v.get('PrimaryURL')} for v in vulns]})
print(json.dumps(output,indent=2))
