"""One-shot local smoke test: uses real Mistral credits and keeps test stories.

Requires its NASA/Python test URLs not to have been submitted yet.
"""
import json
import subprocess
import urllib.request

status = json.loads(subprocess.check_output(['supabase', 'status', '-o', 'json'], stderr=subprocess.DEVNULL))
api = status['API_URL']


def auth(path, payload, key):
    request = urllib.request.Request(api + path, json.dumps(payload).encode(), {
        'Content-Type': 'application/json', 'apikey': key, 'Authorization': 'Bearer ' + key,
    })
    with urllib.request.urlopen(request) as response:
        return json.load(response)


# Use a dedicated local validation account; leave test cards intact for inspection.
link = auth('/auth/v1/admin/generate_link', {'type': 'magiclink', 'email': 'pipeline-validation@example.com'}, status['SERVICE_ROLE_KEY'])
session = auth('/auth/v1/verify', {'type': 'magiclink', 'token_hash': link['hashed_token']}, status['ANON_KEY'])
membership = urllib.request.Request(api + '/rest/v1/group_members', json.dumps({
    'group_id': '00000000-0000-0000-0000-0000000000a1', 'user_id': session['user']['id'],
}).encode(), {'Content-Type': 'application/json', 'apikey': status['SERVICE_ROLE_KEY'],
             'Authorization': 'Bearer ' + status['SERVICE_ROLE_KEY'], 'Prefer': 'resolution=ignore-duplicates'})
with urllib.request.urlopen(membership) as response:
    response.read()


def call(name, payload):
    # Feed secrets through stdin, not command arguments or logs.
    config = '\n'.join([
        'silent', 'show-error', 'max-time = 180', 'request = POST',
        'url = ' + json.dumps(api + '/functions/v1/' + name),
        'header = ' + json.dumps('Content-Type: application/json'),
        'header = ' + json.dumps('Authorization: Bearer ' + session['access_token']),
        'data = ' + json.dumps(json.dumps(payload)),
    ])
    result = subprocess.check_output(['curl', '--config', '-'], input=config.encode())
    data = json.loads(result)
    print(name, json.dumps({key: value for key, value in data.items() if key != 'embedding'}))
    return data


group = '00000000-0000-0000-0000-0000000000a1'
first = call('submit-source', {'group_id': group, 'url': 'https://www.nasa.gov/mission/artemis-ii/', 'comment': 'Integration validation: Artemis II mission.'})
assert first['status'] == 'new_story', first
duplicate = call('submit-source', {'group_id': group, 'url': ' HTTPS://WWW.NASA.GOV/mission/artemis-ii/ '})
assert duplicate['status'] == 'duplicate' and duplicate['source_id'] == first['source_id'], duplicate
related = call('submit-source', {'group_id': group, 'url': 'https://www.nasa.gov/mission/artemis-ii/?preview=related', 'comment': 'Integration validation: alternate URL for the same event.'})
assert related['status'] == 'joined' and related['story_id'] == first['story_id'], related
unrelated = call('submit-source', {'group_id': group, 'url': 'https://www.python.org/downloads/', 'comment': 'Integration validation: unrelated Python downloads.'})
assert unrelated['status'] == 'new_story' and unrelated['story_id'] != first['story_id'], unrelated
reported = call('report-story', {'story_id': first['story_id'], 'target': 'summary', 'note': 'Keep this grounded in the available NASA source metadata.'})
assert reported['id'] == first['story_id'] and not reported['flagged'], reported
print('PASS: new story, duplicate, joined, unrelated new story, report regeneration')
