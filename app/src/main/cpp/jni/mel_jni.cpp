/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (Pexpo adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into Pexpo -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

// JNI bridge to the mel front end.

#include <jni.h>

#include <vector>

#include "analyzer/mel_spectrogram.h"
#include "analyzer/resampler.h"

using bitchord::smart::BeatSpectrogram;
using bitchord::smart::ComputeBeatSpectrogram;
using bitchord::smart::Resample;

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_music_pexpo_playback_smart_MelSpectrogram_nativeResample(
    JNIEnv* env,
    jclass /* clazz */,
    jfloatArray samples,
    jdouble input_rate,
    jdouble output_rate) {
  const jsize count = env->GetArrayLength(samples);
  std::vector<float> input(static_cast<size_t>(count));
  if (count > 0) env->GetFloatArrayRegion(samples, 0, count, input.data());

  const std::vector<float> resampled = Resample(input, input_rate, output_rate);
  const jsize produced = static_cast<jsize>(resampled.size());
  jfloatArray result = env->NewFloatArray(produced);
  if (result == nullptr) return nullptr;
  if (produced > 0) env->SetFloatArrayRegion(result, 0, produced, resampled.data());
  return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_music_pexpo_playback_smart_MelSpectrogram_nativeCompute(
    JNIEnv* env,
    jclass /* clazz */,
    jfloatArray samples,
    jdouble sample_rate) {
  const jsize count = env->GetArrayLength(samples);
  std::vector<float> input(static_cast<size_t>(count));
  if (count > 0) env->GetFloatArrayRegion(samples, 0, count, input.data());

  const BeatSpectrogram spectrogram = ComputeBeatSpectrogram(input, sample_rate);
  const jsize produced = static_cast<jsize>(spectrogram.values.size());
  jfloatArray result = env->NewFloatArray(produced);
  if (result == nullptr) return nullptr;
  if (produced > 0) env->SetFloatArrayRegion(result, 0, produced, spectrogram.values.data());
  return result;
}

JNIEXPORT jint JNICALL
Java_com_music_pexpo_playback_smart_MelSpectrogram_nativeMelCount(
    JNIEnv* /* env */, jclass /* clazz */) {
  return static_cast<jint>(bitchord::smart::kBeatSpectrogramMels);
}

JNIEXPORT jdouble JNICALL
Java_com_music_pexpo_playback_smart_MelSpectrogram_nativeSampleRate(
    JNIEnv* /* env */, jclass /* clazz */) {
  return bitchord::smart::kBeatSpectrogramSampleRate;
}

JNIEXPORT jint JNICALL
Java_com_music_pexpo_playback_smart_MelSpectrogram_nativeHop(
    JNIEnv* /* env */, jclass /* clazz */) {
  return static_cast<jint>(bitchord::smart::kBeatSpectrogramHop);
}

}  // extern "C"
