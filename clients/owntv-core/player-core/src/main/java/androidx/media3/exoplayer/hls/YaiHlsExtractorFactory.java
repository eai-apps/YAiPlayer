/*
 * YAiPlayer: wraps Media3's DefaultHlsExtractorFactory (Apache License 2.0, Copyright The Android Open Source
 * Project). TS segments get YaiHlsTsExtractor, which keeps the audio stream in the preferred language
 * (owner 18.11: Russian on channels whose first MP2 track is English). Everything else is Media3's.
 * Lives in this package to read BundledHlsMediaChunkExtractor.extractor. Keep in sync with Media3 1.11.1.
 */
package androidx.media3.exoplayer.hls;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.ts.YaiHlsTsExtractor;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@UnstableApi
public final class YaiHlsExtractorFactory implements HlsExtractorFactory {

  private static final int DEFAULT_TIMESTAMP_SEARCH_BYTES = 600 * 188;

  private final DefaultHlsExtractorFactory delegate;
  private final int payloadReaderFactoryFlags;
  private final boolean exposeCea608WhenMissingDeclarations;
  private boolean parseSubtitlesDuringExtraction;
  private SubtitleParser.Factory subtitleParserFactory = SubtitleParser.Factory.UNSUPPORTED;

  public YaiHlsExtractorFactory(int payloadReaderFactoryFlags, boolean exposeCea608WhenMissingDeclarations) {
    this.delegate = new DefaultHlsExtractorFactory(payloadReaderFactoryFlags, exposeCea608WhenMissingDeclarations);
    this.payloadReaderFactoryFlags = payloadReaderFactoryFlags;
    this.exposeCea608WhenMissingDeclarations = exposeCea608WhenMissingDeclarations;
  }

  @Override
  public HlsMediaChunkExtractor createExtractor(
      Uri uri,
      Format format,
      @Nullable List<Format> muxedCaptionFormats,
      TimestampAdjuster timestampAdjuster,
      Map<String, List<String>> responseHeaders,
      ExtractorInput sniffingExtractorInput,
      PlayerId playerId)
      throws IOException {
    BundledHlsMediaChunkExtractor chosen =
        delegate.createExtractor(
            uri, format, muxedCaptionFormats, timestampAdjuster, responseHeaders, sniffingExtractorInput, playerId);
    if (!(chosen.extractor instanceof TsExtractor)) return chosen;
    return new TsChunk(createTs(format, muxedCaptionFormats, timestampAdjuster));
  }

  /** Media3's createTsExtractor, building the YAiPlayer copy. */
  private YaiHlsTsExtractor createTs(
      Format format, @Nullable List<Format> muxedCaptionFormats, TimestampAdjuster timestampAdjuster) {
    int flags = DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM | payloadReaderFactoryFlags;
    if (muxedCaptionFormats != null) {
      flags |= DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS;
    } else if (exposeCea608WhenMissingDeclarations) {
      muxedCaptionFormats =
          Collections.singletonList(new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build());
    } else {
      muxedCaptionFormats = Collections.emptyList();
    }
    @Nullable String codecs = format.codecs;
    if (!TextUtils.isEmpty(codecs)) {
      if (!MimeTypes.containsCodecsCorrespondingToMimeType(codecs, MimeTypes.AUDIO_AAC)) {
        flags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_AAC_STREAM;
      }
      if (!MimeTypes.containsCodecsCorrespondingToMimeType(codecs, MimeTypes.VIDEO_H264)) {
        flags |= DefaultTsPayloadReaderFactory.FLAG_IGNORE_H264_STREAM;
      }
    }
    int extractorFlags = 0;
    SubtitleParser.Factory parserFactory = subtitleParserFactory;
    if (!parseSubtitlesDuringExtraction) {
      parserFactory = SubtitleParser.Factory.UNSUPPORTED;
      extractorFlags |= YaiHlsTsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA;
    }
    return new YaiHlsTsExtractor(
        YaiHlsTsExtractor.MODE_HLS,
        extractorFlags,
        parserFactory,
        timestampAdjuster,
        new DefaultTsPayloadReaderFactory(flags, muxedCaptionFormats),
        DEFAULT_TIMESTAMP_SEARCH_BYTES);
  }

  @Override
  public HlsExtractorFactory setSubtitleParserFactory(SubtitleParser.Factory subtitleParserFactory) {
    this.subtitleParserFactory = subtitleParserFactory;
    delegate.setSubtitleParserFactory(subtitleParserFactory);
    return this;
  }

  @Override
  public HlsExtractorFactory experimentalParseSubtitlesDuringExtraction(boolean parseSubtitlesDuringExtraction) {
    this.parseSubtitlesDuringExtraction = parseSubtitlesDuringExtraction;
    delegate.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction);
    return this;
  }

  @Override
  public HlsExtractorFactory experimentalSetCodecsToParseWithinGopSampleDependencies(int codecsToParseWithinGopSampleDependencies) {
    delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies);
    return this;
  }

  @Override
  public Format getOutputTextFormat(Format sourceFormat) {
    return delegate.getOutputTextFormat(sourceFormat);
  }

  /** A reusable TS segment extractor, as Media3's bundled one is for its own TsExtractor. */
  private static final class TsChunk implements HlsMediaChunkExtractor {
    private static final PositionHolder POSITION_HOLDER = new PositionHolder();
    private final Extractor extractor;

    TsChunk(Extractor extractor) {
      this.extractor = extractor;
    }

    @Override
    public void init(ExtractorOutput extractorOutput) {
      extractor.init(extractorOutput);
    }

    @Override
    public boolean read(ExtractorInput extractorInput) throws IOException {
      return extractor.read(extractorInput, POSITION_HOLDER) == Extractor.RESULT_CONTINUE;
    }

    @Override
    public boolean isPackedAudioExtractor() {
      return false;
    }

    @Override
    public boolean isReusable() {
      return true;
    }

    @Override
    public HlsMediaChunkExtractor recreate() {
      throw new IllegalStateException("TS segment extractor is reusable");
    }

    @Override
    public void onTruncatedSegmentParsed() {
      extractor.seek(/* position= */ 0, /* timeUs= */ 0);
    }
  }
}
