> This is a page from the ElevenLabs documentation. For a complete page index, fetch https://elevenlabs.io/docs/llms.txt. For the full documentation in a single file, fetch https://elevenlabs.io/docs/llms-full.txt.

# Create transcript

POST https://api.elevenlabs.io/v1/speech-to-text
Content-Type: multipart/form-data

Transcribe an audio or video file. If webhook is set to true, the request will be processed asynchronously and results sent to configured webhooks. When use_multi_channel is true and the provided audio has multiple channels, a 'transcripts' object with separate transcripts for each channel is returned; set multichannel_output_style='combined' to instead receive a single transcript with all channels merged and sorted by time. Otherwise, returns a single transcript. The optional webhook_metadata parameter allows you to attach custom data that will be included in webhook responses for request correlation and tracking.

Reference: https://elevenlabs.io/docs/api-reference/speech-to-text/convert

## Servers

- `https://api.elevenlabs.io` (Production, default)
- `https://api.us.elevenlabs.io` (Production US)
- `https://api.eu.residency.elevenlabs.io` (Production EU)
- `https://api.in.residency.elevenlabs.io` (Production India)
- `https://api.sg.residency.elevenlabs.io` (Production Singapore)

## Request

### Query parameters

- `token` (string, optional, nullable) — A single-use authentication token created via POST /v1/single-use-token/batch_scribe. This token can only be used once and expires after 15 minutes. Alternative to API key or bearer token authentication for frontend clients.
- `enable_logging` (boolean, optional, default: true) — When enable_logging is set to false zero retention mode will be used for the request. This will mean log and transcript storage features are unavailable for this request. Zero retention mode may only be used by enterprise customers.

### Body (multipart/form-data)

This endpoint expects a multipart form containing an optional file.

- `model_id` (string, required) — The ID of the model to use for transcription.
- `file` (file, optional) — The file to transcribe (100ms minimum audio length). All major audio and video formats are supported. Exactly one of the file or cloud_storage_url parameters must be provided. The file size must be less than 5.0GB.
- `language_code` (string, optional) — An ISO-639-1 or ISO-639-3 language_code corresponding to the language of the audio file. Can sometimes improve transcription performance if known beforehand. Defaults to null, in this case the language is predicted automatically.
- `transcript_edit` (string, optional) — Natural-language instruction applied to the final transcript (max 2000 characters). The edited text is returned in 'edited_transcript' alongside the original transcript. Cannot be combined with entity_detection, entity_redaction or use_multi_channel. Usage of this parameter will incur an additional 30% surcharge on the base transcription cost, billed for at least 10 seconds of audio.
- `tag_audio_events` (boolean, optional) — Whether to tag audio events like (laughter), (footsteps), etc. in the transcription.
- `num_speakers` (integer, optional) — The maximum amount of speakers talking in the uploaded file. Can help with predicting who speaks when. The maximum amount of speakers that can be predicted is 32. Defaults to null, in this case the amount of speakers is set to the maximum value the model supports.
- `timestamps_granularity` (enum, optional) — The granularity of the timestamps in the transcription. 'word' provides word-level timestamps and 'character' provides character-level timestamps per word.
- `diarize` (boolean, optional) — Whether to annotate which speaker is currently talking in the uploaded file.
- `diarization_threshold` (double, optional) — Diarization threshold to apply during speaker diarization. A higher value means there will be a lower chance of one speaker being diarized as two different speakers but also a higher chance of two different speakers being diarized as one speaker (less total speakers predicted). A low value means there will be a higher chance of one speaker being diarized as two different speakers but also a lower chance of two different speakers being diarized as one speaker (more total speakers predicted). Can only be set when diarize=True and num_speakers=None. Defaults to None, in which case we will choose a threshold based on the model_id (0.22 usually).
- `additional_formats` (list of ExportOptions, optional) — A list of additional formats to export the transcript to.
- `file_format` (enum, optional) — The format of input audio. Options are 'pcm_s16le_16' or 'other' For `pcm_s16le_16`, the input audio must be 16-bit PCM at a 16kHz sample rate, single channel (mono), and little-endian byte order. Latency will be lower than with passing an encoded waveform.
- `cloud_storage_url` (string, optional) — [Deprecated] This parameter is deprecated and will be removed in the future. Use 'source_url' instead.The HTTPS URL of the file to transcribe. Exactly one of the file or cloud_storage_url parameters must be provided. The file must be accessible via HTTPS and the file size must be less than 2GB. Any valid HTTPS URL is accepted, including URLs from cloud storage providers (AWS S3, Google Cloud Storage, Cloudflare R2, etc.), CDNs, or any other HTTPS source. URLs can be pre-signed or include authentication tokens in query parameters.
- `source_url` (string, optional) — The URL of an audio or video file to transcribe. Supports hosted video or audio files, YouTube video URLs, TikTok video URLs, and other video hosting services.
- `webhook` (boolean, optional) — Whether to send the transcription result to configured speech-to-text webhooks. If set the request will return early without the transcription, which will be delivered later via webhook.
- `webhook_id` (string, optional) — Optional specific webhook ID to send the transcription result to. Only valid when webhook is set to true. If not provided, transcription will be sent to all configured speech-to-text webhooks.
- `temperature` (double, optional) — Controls the randomness of the transcription output. Accepts values between 0.0 and 2.0, where higher values result in more diverse and less deterministic results. If omitted, we will use a temperature based on the model you selected which is usually 0.
- `seed` (integer, optional) — If specified, our system will make a best effort to sample deterministically, such that repeated requests with the same seed and parameters should return the same result. Determinism is not guaranteed. Must be an integer between 0 and 2147483647.
- `use_multi_channel` (boolean, optional) — Whether the audio file contains multiple channels where each channel contains a single speaker. When enabled, each channel is transcribed independently. By default a separate transcript is returned per channel; set multichannel_output_style='combined' to instead receive a single transcript with all channels merged and sorted by time. Each word in the response includes a 'channel_index' field indicating which channel it was spoken on. A maximum of 5 channels is supported. Each channel is billed independently at the full audio duration, so cost scales linearly with the number of channels.
- `multichannel_output_style` (enum, optional) — Controls the response shape when use_multi_channel is enabled. 'separate' (default) returns one transcript per channel under 'transcripts'. 'combined' merges all channels into a single transcript whose words are sorted by start time, each carrying a 'channel_index' - matching the single-channel response shape. 'combined' requires timestamps (timestamps_granularity must not be 'none') and does not support entity detection or redaction.
- `webhook_metadata` (V1SpeechToTextPostRequestBodyContentMultipartFormDataSchemaWebhookMetadata, optional) — Optional metadata to be included in the webhook response. This should be a JSON string representing an object with a maximum depth of 2 levels and maximum size of 16KB. Useful for tracking internal IDs, job references, or other contextual information.
- `entity_detection` (V1SpeechToTextPostRequestBodyContentMultipartFormDataSchemaEntityDetection, optional) — Detect entities in the transcript. Can be 'all' to detect all entities, a single entity type or category string, or a list of entity types/categories. Categories include 'pii', 'phi', 'pci', 'other', 'offensive_language'. When enabled, detected entities will be returned in the 'entities' field with their text, type, and character positions.
- `no_verbatim` (boolean, optional) — If true, the transcription will not have any filler words, false starts and non-speech sounds.
- `use_speaker_library` (boolean, optional) — Whether to use the speaker library for identifying known speakers during diarization. When enabled and diarize is true, detected speakers will be matched against registered speakers in the workspace's speaker library.
- `detect_speaker_roles` (boolean, optional) — Whether to detect speaker roles (agent vs customer). Requires diarize=true. Cannot be used with use_multi_channel=true. When enabled, speaker_id values will be 'agent' and 'customer' instead of 'speaker_0', 'speaker_1', etc. Usage incurs an additional 10% surcharge on base transcription cost.
- `entity_redaction` (V1SpeechToTextPostRequestBodyContentMultipartFormDataSchemaEntityRedaction, optional) — Redact entities from the transcript text. Accepts the same format as entity_detection: 'all', a category ('pii', 'phi'), or specific entity types. Must be a subset of entity_detection. When redaction is enabled, the entities field will not be returned.
- `entity_redaction_mode` (string, optional) — How to format redacted entities. 'redacted' replaces with \{REDACTED}, 'entity\_type' replaces with \{ENTITY\_TYPE}, 'enumerated\_entity\_type' replaces with \{ENTITY\_TYPE\_N} where N enumerates each occurrence. Only used when entity\_redaction is set.
- `keyterms` (list of string, optional) — A list of keyterms to bias the transcription towards. The keyterms are words or phrases you want the model to recognise more accurately. The number of keyterms cannot exceed 1000. The length of each keyterm must be at most 50 characters. Keyterms can contain at most 5 words (after normalisation). For example \["hello", "world", "technical term"]. The following characters are not supported: `<`, `>`, `{`, `}`, `[`, `]`, `\`. Usage of this parameter will incur an additional 20% surcharge on the base transcription cost. When more than 100 keyterms are provided, a minimum billable duration of 20 seconds applies per request.

## Response

### 200

Synchronous transcription result

- `speech_to_text_convert_Response_200`
  - SpeechToTextChunkResponseModel: SpeechToTextChunkResponseModel
  - MultichannelSpeechToTextResponseModel: MultichannelSpeechToTextResponseModel
  - SpeechToTextWebhookResponseModel: SpeechToTextWebhookResponseModel

### 202

Asynchronous request accepted

## Errors

### 422 Unprocessable Entity Error

Validation Error

- `detail` (list of ValidationError, optional)

## Types

### SpeechToTextChunkResponseModel

Chunk-level detail of the transcription with timing information.

- `language_code` (string, required) — The detected language code (e.g. 'eng' for English).
- `language_probability` (double, required) — The confidence score of the language detection (0 to 1).
- `text` (string, required) — The raw text of the transcription.
- `words` (list of SpeechToTextWordResponseModel, required) — List of words with their timing information.
- `channel_index` (integer, optional, nullable) — The channel index this transcript belongs to (for multichannel audio).
- `additional_formats` (list of AdditionalFormatResponseModel, optional, nullable) — Requested additional formats of the transcript.
- `transcription_id` (string, optional, nullable) — The transcription ID of the response.
- `entities` (list of DetectedEntity, optional, nullable) — List of detected entities with their text, type, and character positions in the transcript.
- `audio_duration_secs` (double, optional, nullable) — The duration of the audio that was transcribed in seconds.
- `edited_transcript` (SpeechToTextChunkResponseModelEditedTranscript, optional, nullable) — Result of the optional transcript edit: the edited text, or an error if it could not be produced. Absent when no edit was requested. On success, both `text` and `edited_text` hold the edited transcript; compare them with the top-level `text` to see what changed.

### MultichannelSpeechToTextResponseModel

Response model for multichannel speech-to-text transcription.

- `transcripts` (list of SpeechToTextChunkResponseModel, required) — List of transcripts, one for each audio channel. Each transcript contains the text and word-level details for its respective channel.
- `transcription_id` (string, optional, nullable) — The transcription ID of the response.
- `audio_duration_secs` (double, optional, nullable) — The duration of the audio that was transcribed across all channels in seconds.

### SpeechToTextWebhookResponseModel

- `message` (string, required) — The message of the webhook response.
- `request_id` (string, required) — The request ID of the webhook response.
- `transcription_id` (string, optional, nullable) — The transcription ID of the webhook response.

### ValidationError

- `loc` (list of ValidationErrorLocItems, required)
- `msg` (string, required)
- `type` (string, required)

### SpeechToTextWordResponseModel

Word-level detail of the transcription with timing information.

- `text` (string, required) — The word or sound that was transcribed.
- `type` (enum, required) — The type of the word or sound. 'audio_event' is used for non-word sounds like laughter or footsteps.
  - Allowed values: `word`, `spacing`, `audio_event`
- `logprob` (double, required) — The log of the probability with which this word was predicted. Logprobs are in range [-infinity, 0], higher logprobs indicate a higher confidence the model has in its predictions.
- `start` (double, optional, nullable) — The start time of the word or sound in seconds.
- `end` (double, optional, nullable) — The end time of the word or sound in seconds.
- `speaker_id` (string, optional, nullable) — Unique identifier for the speaker of this word.
- `characters` (list of SpeechToTextCharacterResponseModel, optional, nullable) — The characters that make up the word and their timing information.
- `channel_index` (integer, optional, nullable) — The channel this word was spoken on (for multichannel audio). Null for single-channel transcriptions.

### AdditionalFormatResponseModel

- `requested_format` (string, required) — The requested format.
- `file_extension` (string, required) — The file extension of the additional format.
- `content_type` (string, required) — The content type of the additional format.
- `is_base64_encoded` (boolean, required) — Whether the content is base64 encoded.
- `content` (string, required) — The content of the additional format.

### DetectedEntity

An entity detected within transcribed text.

- `text` (string, required) — The text that was identified as an entity.
- `entity_type` (string, required) — The type of entity detected (e.g., 'credit_card', 'email_address', 'person_name').
- `start_char` (integer, required) — Start character position in the transcript text.
- `end_char` (integer, required) — End character position in the transcript text.

### SpeechToTextChunkResponseModelEditedTranscript

Result of the optional transcript edit: the edited text, or an error if it could not be produced. Absent when no edit was requested. On success, both `text` and `edited_text` hold the edited transcript; compare them with the top-level `text` to see what changed.

- `kind`: `error` (TranscriptEditError)
  - `error_type` ("edit_failed", required) — edit_failed: the edit could not be produced.
  - `message` (string, required) — A short, user-facing explanation of the failure.
- `kind`: `transcript` (EditedTranscript)
  - `edited_text` (string, required) — The edited transcript text. If no edits were made it will be identical to the `text` field.
  - `text` (string, required) — The committed transcript text the edit instruction was applied to.
  - `message_type` (string, optional, default: edited_transcript)

### ValidationErrorLocItems

- string
- integer

### SpeechToTextCharacterResponseModel

- `text` (string, required) — The character that was transcribed.
- `start` (double, optional, nullable) — The start time of the character in seconds.
- `end` (double, optional, nullable) — The end time of the character in seconds.

## Examples

### Example_0

**Request**

```json
{
  "file": "<file: <file1>>",
  "model_id": "scribe_v2"
}
```

**Response**

```json
{
  "language_code": "en",
  "language_probability": 0.98,
  "text": "Hello world!",
  "words": [
    {
      "end": 0.5,
      "logprob": -0.124,
      "speaker_id": "speaker_1",
      "start": 0,
      "text": "Hello",
      "type": "word"
    }
  ]
}
```

**SDK Code**

```python Example_0
import requests

url = "https://api.elevenlabs.io/v1/speech-to-text"

querystring = {"enable_logging":"true","token":"token"}

files = { "file": "open('<file1>', 'rb')" }
payload = {
    "additional_formats": ,
    "cloud_storage_url": ,
    "detect_speaker_roles": ,
    "diarization_threshold": ,
    "diarize": ,
    "entity_detection": ,
    "entity_redaction": ,
    "entity_redaction_mode": ,
    "file_format": ,
    "keyterms": ,
    "language_code": ,
    "model_id": "scribe_v2",
    "multichannel_output_style": ,
    "no_verbatim": ,
    "num_speakers": ,
    "seed": ,
    "source_url": ,
    "tag_audio_events": ,
    "temperature": ,
    "timestamps_granularity": ,
    "transcript_edit": ,
    "use_multi_channel": ,
    "use_speaker_library": ,
    "webhook": ,
    "webhook_id": ,
    "webhook_metadata": 
}
headers = {"xi-api-key": "xi-api-key"}

response = requests.post(url, data=payload, files=files, headers=headers, params=querystring)

print(response.json())
```

```javascript Example_0
const url = 'https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token';
const form = new FormData();
form.append('additional_formats', '');
form.append('cloud_storage_url', '');
form.append('detect_speaker_roles', '');
form.append('diarization_threshold', '');
form.append('diarize', '');
form.append('entity_detection', '');
form.append('entity_redaction', '');
form.append('entity_redaction_mode', '');
form.append('file', '<file1>');
form.append('file_format', '');
form.append('keyterms', '');
form.append('language_code', '');
form.append('model_id', 'scribe_v2');
form.append('multichannel_output_style', '');
form.append('no_verbatim', '');
form.append('num_speakers', '');
form.append('seed', '');
form.append('source_url', '');
form.append('tag_audio_events', '');
form.append('temperature', '');
form.append('timestamps_granularity', '');
form.append('transcript_edit', '');
form.append('use_multi_channel', '');
form.append('use_speaker_library', '');
form.append('webhook', '');
form.append('webhook_id', '');
form.append('webhook_metadata', '');

const options = {method: 'POST', headers: {'xi-api-key': 'xi-api-key'}};

options.body = form;

try {
  const response = await fetch(url, options);
  const data = await response.json();
  console.log(data);
} catch (error) {
  console.error(error);
}
```

```go Example_0
package main

import (
	"fmt"
	"strings"
	"net/http"
	"io"
)

func main() {

	url := "https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token"

	payload := strings.NewReader("-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"additional_formats\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"cloud_storage_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"detect_speaker_roles\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarization_threshold\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarize\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_detection\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction_mode\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file\"; filename=\"<file1>\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file_format\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"keyterms\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"language_code\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"model_id\"\r\n\r\nscribe_v2\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"multichannel_output_style\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"no_verbatim\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"num_speakers\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"seed\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"source_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"tag_audio_events\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"temperature\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"timestamps_granularity\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"transcript_edit\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_multi_channel\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_speaker_library\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_id\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_metadata\"\r\n\r\n\r\n-----011000010111000001101001--\r\n")

	req, _ := http.NewRequest("POST", url, payload)

	req.Header.Add("xi-api-key", "xi-api-key")

	res, _ := http.DefaultClient.Do(req)

	defer res.Body.Close()
	body, _ := io.ReadAll(res.Body)

	fmt.Println(res)
	fmt.Println(string(body))

}
```

```ruby Example_0
require 'uri'
require 'net/http'

url = URI("https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token")

http = Net::HTTP.new(url.host, url.port)
http.use_ssl = true

request = Net::HTTP::Post.new(url)
request["xi-api-key"] = 'xi-api-key'
request.body = "-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"additional_formats\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"cloud_storage_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"detect_speaker_roles\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarization_threshold\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarize\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_detection\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction_mode\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file\"; filename=\"<file1>\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file_format\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"keyterms\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"language_code\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"model_id\"\r\n\r\nscribe_v2\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"multichannel_output_style\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"no_verbatim\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"num_speakers\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"seed\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"source_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"tag_audio_events\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"temperature\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"timestamps_granularity\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"transcript_edit\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_multi_channel\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_speaker_library\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_id\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_metadata\"\r\n\r\n\r\n-----011000010111000001101001--\r\n"

response = http.request(request)
puts response.read_body
```

```java Example_0
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;

HttpResponse<String> response = Unirest.post("https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token")
  .header("xi-api-key", "xi-api-key")
  .body("-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"additional_formats\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"cloud_storage_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"detect_speaker_roles\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarization_threshold\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarize\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_detection\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction_mode\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file\"; filename=\"<file1>\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file_format\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"keyterms\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"language_code\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"model_id\"\r\n\r\nscribe_v2\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"multichannel_output_style\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"no_verbatim\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"num_speakers\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"seed\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"source_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"tag_audio_events\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"temperature\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"timestamps_granularity\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"transcript_edit\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_multi_channel\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_speaker_library\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_id\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_metadata\"\r\n\r\n\r\n-----011000010111000001101001--\r\n")
  .asString();
```

```php Example_0
<?php
require_once('vendor/autoload.php');

$client = new \GuzzleHttp\Client();

$response = $client->request('POST', 'https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token', [
  'multipart' => [
    [
        'name' => 'file',
        'filename' => '<file1>',
        'contents' => null
    ],
    [
        'name' => 'model_id',
        'contents' => 'scribe_v2'
    ]
  ]
  'headers' => [
    'xi-api-key' => 'xi-api-key',
  ],
]);

echo $response->getBody();
```

```csharp Example_0
using RestSharp;

var client = new RestClient("https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token");
var request = new RestRequest(Method.POST);
request.AddHeader("xi-api-key", "xi-api-key");
request.AddParameter("undefined", "-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"additional_formats\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"cloud_storage_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"detect_speaker_roles\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarization_threshold\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarize\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_detection\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction_mode\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file\"; filename=\"<file1>\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file_format\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"keyterms\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"language_code\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"model_id\"\r\n\r\nscribe_v2\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"multichannel_output_style\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"no_verbatim\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"num_speakers\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"seed\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"source_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"tag_audio_events\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"temperature\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"timestamps_granularity\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"transcript_edit\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_multi_channel\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_speaker_library\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_id\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_metadata\"\r\n\r\n\r\n-----011000010111000001101001--\r\n", ParameterType.RequestBody);
IRestResponse response = client.Execute(request);
```

```swift Example_0
import Foundation

let headers = ["xi-api-key": "xi-api-key"]
let parameters = [
  [
    "name": "additional_formats",
    "value": 
  ],
  [
    "name": "cloud_storage_url",
    "value": 
  ],
  [
    "name": "detect_speaker_roles",
    "value": 
  ],
  [
    "name": "diarization_threshold",
    "value": 
  ],
  [
    "name": "diarize",
    "value": 
  ],
  [
    "name": "entity_detection",
    "value": 
  ],
  [
    "name": "entity_redaction",
    "value": 
  ],
  [
    "name": "entity_redaction_mode",
    "value": 
  ],
  [
    "name": "file",
    "fileName": "<file1>"
  ],
  [
    "name": "file_format",
    "value": 
  ],
  [
    "name": "keyterms",
    "value": 
  ],
  [
    "name": "language_code",
    "value": 
  ],
  [
    "name": "model_id",
    "value": "scribe_v2"
  ],
  [
    "name": "multichannel_output_style",
    "value": 
  ],
  [
    "name": "no_verbatim",
    "value": 
  ],
  [
    "name": "num_speakers",
    "value": 
  ],
  [
    "name": "seed",
    "value": 
  ],
  [
    "name": "source_url",
    "value": 
  ],
  [
    "name": "tag_audio_events",
    "value": 
  ],
  [
    "name": "temperature",
    "value": 
  ],
  [
    "name": "timestamps_granularity",
    "value": 
  ],
  [
    "name": "transcript_edit",
    "value": 
  ],
  [
    "name": "use_multi_channel",
    "value": 
  ],
  [
    "name": "use_speaker_library",
    "value": 
  ],
  [
    "name": "webhook",
    "value": 
  ],
  [
    "name": "webhook_id",
    "value": 
  ],
  [
    "name": "webhook_metadata",
    "value": 
  ]
]

let boundary = "---011000010111000001101001"

var body = ""
var error: NSError? = nil
for param in parameters {
  let paramName = param["name"]!
  body += "--\(boundary)\r\n"
  body += "Content-Disposition:form-data; name=\"\(paramName)\""
  if let filename = param["fileName"] {
    let contentType = param["content-type"]!
    let fileContent = String(contentsOfFile: filename, encoding: String.Encoding.utf8)
    if (error != nil) {
      print(error as Any)
    }
    body += "; filename=\"\(filename)\"\r\n"
    body += "Content-Type: \(contentType)\r\n\r\n"
    body += fileContent
  } else if let paramValue = param["value"] {
    body += "\r\n\r\n\(paramValue)"
  }
}

let request = NSMutableURLRequest(url: NSURL(string: "https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token")! as URL,
                                        cachePolicy: .useProtocolCachePolicy,
                                    timeoutInterval: 10.0)
request.httpMethod = "POST"
request.allHTTPHeaderFields = headers
request.httpBody = postData as Data

let session = URLSession.shared
let dataTask = session.dataTask(with: request as URLRequest, completionHandler: { (data, response, error) -> Void in
  if (error != nil) {
    print(error as Any)
  } else {
    let httpResponse = response as? HTTPURLResponse
    print(httpResponse)
  }
})

dataTask.resume()
```

### Example_1

**Request**

```json
{
  "file": "<file: <file1>>",
  "model_id": "scribe_v2"
}
```

**Response**

```json
{
  "transcripts": [
    {
      "language_code": "en",
      "language_probability": 0.98,
      "text": "Hello from channel one.",
      "words": [
        {
          "channel_index": 0,
          "end": 0.5,
          "logprob": -0.124,
          "speaker_id": "speaker_0",
          "start": 0,
          "text": "Hello",
          "type": "word"
        }
      ]
    },
    {
      "language_code": "en",
      "language_probability": 0.97,
      "text": "Greetings from channel two.",
      "words": [
        {
          "channel_index": 1,
          "end": 0.7,
          "logprob": -0.156,
          "speaker_id": "speaker_1",
          "start": 0.1,
          "text": "Greetings",
          "type": "word"
        }
      ]
    }
  ]
}
```

**SDK Code**

```python Example_1
import requests

url = "https://api.elevenlabs.io/v1/speech-to-text"

querystring = {"enable_logging":"true","token":"token"}

files = { "file": "open('<file1>', 'rb')" }
payload = {
    "additional_formats": ,
    "cloud_storage_url": ,
    "detect_speaker_roles": ,
    "diarization_threshold": ,
    "diarize": ,
    "entity_detection": ,
    "entity_redaction": ,
    "entity_redaction_mode": ,
    "file_format": ,
    "keyterms": ,
    "language_code": ,
    "model_id": "scribe_v2",
    "multichannel_output_style": ,
    "no_verbatim": ,
    "num_speakers": ,
    "seed": ,
    "source_url": ,
    "tag_audio_events": ,
    "temperature": ,
    "timestamps_granularity": ,
    "transcript_edit": ,
    "use_multi_channel": ,
    "use_speaker_library": ,
    "webhook": ,
    "webhook_id": ,
    "webhook_metadata": 
}
headers = {"xi-api-key": "xi-api-key"}

response = requests.post(url, data=payload, files=files, headers=headers, params=querystring)

print(response.json())
```

```javascript Example_1
const url = 'https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token';
const form = new FormData();
form.append('additional_formats', '');
form.append('cloud_storage_url', '');
form.append('detect_speaker_roles', '');
form.append('diarization_threshold', '');
form.append('diarize', '');
form.append('entity_detection', '');
form.append('entity_redaction', '');
form.append('entity_redaction_mode', '');
form.append('file', '<file1>');
form.append('file_format', '');
form.append('keyterms', '');
form.append('language_code', '');
form.append('model_id', 'scribe_v2');
form.append('multichannel_output_style', '');
form.append('no_verbatim', '');
form.append('num_speakers', '');
form.append('seed', '');
form.append('source_url', '');
form.append('tag_audio_events', '');
form.append('temperature', '');
form.append('timestamps_granularity', '');
form.append('transcript_edit', '');
form.append('use_multi_channel', '');
form.append('use_speaker_library', '');
form.append('webhook', '');
form.append('webhook_id', '');
form.append('webhook_metadata', '');

const options = {method: 'POST', headers: {'xi-api-key': 'xi-api-key'}};

options.body = form;

try {
  const response = await fetch(url, options);
  const data = await response.json();
  console.log(data);
} catch (error) {
  console.error(error);
}
```

```go Example_1
package main

import (
	"fmt"
	"strings"
	"net/http"
	"io"
)

func main() {

	url := "https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token"

	payload := strings.NewReader("-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"additional_formats\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"cloud_storage_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"detect_speaker_roles\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarization_threshold\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarize\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_detection\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction_mode\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file\"; filename=\"<file1>\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file_format\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"keyterms\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"language_code\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"model_id\"\r\n\r\nscribe_v2\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"multichannel_output_style\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"no_verbatim\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"num_speakers\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"seed\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"source_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"tag_audio_events\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"temperature\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"timestamps_granularity\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"transcript_edit\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_multi_channel\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_speaker_library\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_id\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_metadata\"\r\n\r\n\r\n-----011000010111000001101001--\r\n")

	req, _ := http.NewRequest("POST", url, payload)

	req.Header.Add("xi-api-key", "xi-api-key")

	res, _ := http.DefaultClient.Do(req)

	defer res.Body.Close()
	body, _ := io.ReadAll(res.Body)

	fmt.Println(res)
	fmt.Println(string(body))

}
```

```ruby Example_1
require 'uri'
require 'net/http'

url = URI("https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token")

http = Net::HTTP.new(url.host, url.port)
http.use_ssl = true

request = Net::HTTP::Post.new(url)
request["xi-api-key"] = 'xi-api-key'
request.body = "-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"additional_formats\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"cloud_storage_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"detect_speaker_roles\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarization_threshold\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarize\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_detection\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction_mode\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file\"; filename=\"<file1>\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file_format\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"keyterms\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"language_code\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"model_id\"\r\n\r\nscribe_v2\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"multichannel_output_style\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"no_verbatim\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"num_speakers\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"seed\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"source_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"tag_audio_events\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"temperature\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"timestamps_granularity\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"transcript_edit\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_multi_channel\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_speaker_library\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_id\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_metadata\"\r\n\r\n\r\n-----011000010111000001101001--\r\n"

response = http.request(request)
puts response.read_body
```

```java Example_1
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;

HttpResponse<String> response = Unirest.post("https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token")
  .header("xi-api-key", "xi-api-key")
  .body("-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"additional_formats\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"cloud_storage_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"detect_speaker_roles\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarization_threshold\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarize\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_detection\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction_mode\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file\"; filename=\"<file1>\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file_format\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"keyterms\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"language_code\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"model_id\"\r\n\r\nscribe_v2\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"multichannel_output_style\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"no_verbatim\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"num_speakers\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"seed\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"source_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"tag_audio_events\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"temperature\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"timestamps_granularity\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"transcript_edit\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_multi_channel\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_speaker_library\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_id\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_metadata\"\r\n\r\n\r\n-----011000010111000001101001--\r\n")
  .asString();
```

```php Example_1
<?php
require_once('vendor/autoload.php');

$client = new \GuzzleHttp\Client();

$response = $client->request('POST', 'https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token', [
  'multipart' => [
    [
        'name' => 'file',
        'filename' => '<file1>',
        'contents' => null
    ],
    [
        'name' => 'model_id',
        'contents' => 'scribe_v2'
    ]
  ]
  'headers' => [
    'xi-api-key' => 'xi-api-key',
  ],
]);

echo $response->getBody();
```

```csharp Example_1
using RestSharp;

var client = new RestClient("https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token");
var request = new RestRequest(Method.POST);
request.AddHeader("xi-api-key", "xi-api-key");
request.AddParameter("undefined", "-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"additional_formats\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"cloud_storage_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"detect_speaker_roles\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarization_threshold\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"diarize\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_detection\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"entity_redaction_mode\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file\"; filename=\"<file1>\"\r\nContent-Type: application/octet-stream\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file_format\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"keyterms\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"language_code\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"model_id\"\r\n\r\nscribe_v2\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"multichannel_output_style\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"no_verbatim\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"num_speakers\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"seed\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"source_url\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"tag_audio_events\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"temperature\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"timestamps_granularity\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"transcript_edit\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_multi_channel\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"use_speaker_library\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_id\"\r\n\r\n\r\n-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"webhook_metadata\"\r\n\r\n\r\n-----011000010111000001101001--\r\n", ParameterType.RequestBody);
IRestResponse response = client.Execute(request);
```

```swift Example_1
import Foundation

let headers = ["xi-api-key": "xi-api-key"]
let parameters = [
  [
    "name": "additional_formats",
    "value": 
  ],
  [
    "name": "cloud_storage_url",
    "value": 
  ],
  [
    "name": "detect_speaker_roles",
    "value": 
  ],
  [
    "name": "diarization_threshold",
    "value": 
  ],
  [
    "name": "diarize",
    "value": 
  ],
  [
    "name": "entity_detection",
    "value": 
  ],
  [
    "name": "entity_redaction",
    "value": 
  ],
  [
    "name": "entity_redaction_mode",
    "value": 
  ],
  [
    "name": "file",
    "fileName": "<file1>"
  ],
  [
    "name": "file_format",
    "value": 
  ],
  [
    "name": "keyterms",
    "value": 
  ],
  [
    "name": "language_code",
    "value": 
  ],
  [
    "name": "model_id",
    "value": "scribe_v2"
  ],
  [
    "name": "multichannel_output_style",
    "value": 
  ],
  [
    "name": "no_verbatim",
    "value": 
  ],
  [
    "name": "num_speakers",
    "value": 
  ],
  [
    "name": "seed",
    "value": 
  ],
  [
    "name": "source_url",
    "value": 
  ],
  [
    "name": "tag_audio_events",
    "value": 
  ],
  [
    "name": "temperature",
    "value": 
  ],
  [
    "name": "timestamps_granularity",
    "value": 
  ],
  [
    "name": "transcript_edit",
    "value": 
  ],
  [
    "name": "use_multi_channel",
    "value": 
  ],
  [
    "name": "use_speaker_library",
    "value": 
  ],
  [
    "name": "webhook",
    "value": 
  ],
  [
    "name": "webhook_id",
    "value": 
  ],
  [
    "name": "webhook_metadata",
    "value": 
  ]
]

let boundary = "---011000010111000001101001"

var body = ""
var error: NSError? = nil
for param in parameters {
  let paramName = param["name"]!
  body += "--\(boundary)\r\n"
  body += "Content-Disposition:form-data; name=\"\(paramName)\""
  if let filename = param["fileName"] {
    let contentType = param["content-type"]!
    let fileContent = String(contentsOfFile: filename, encoding: String.Encoding.utf8)
    if (error != nil) {
      print(error as Any)
    }
    body += "; filename=\"\(filename)\"\r\n"
    body += "Content-Type: \(contentType)\r\n\r\n"
    body += fileContent
  } else if let paramValue = param["value"] {
    body += "\r\n\r\n\(paramValue)"
  }
}

let request = NSMutableURLRequest(url: NSURL(string: "https://api.elevenlabs.io/v1/speech-to-text?enable_logging=true&token=token")! as URL,
                                        cachePolicy: .useProtocolCachePolicy,
                                    timeoutInterval: 10.0)
request.httpMethod = "POST"
request.allHTTPHeaderFields = headers
request.httpBody = postData as Data

let session = URLSession.shared
let dataTask = session.dataTask(with: request as URLRequest, completionHandler: { (data, response, error) -> Void in
  if (error != nil) {
    print(error as Any)
  } else {
    let httpResponse = response as? HTTPURLResponse
    print(httpResponse)
  }
})

dataTask.resume()
```
