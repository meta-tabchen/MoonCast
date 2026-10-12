package com.mooncast.tv.media;
import java.util.concurrent.CompletableFuture;
public final class MediaStopRegistryTest {
 public static void main(String[] args) {
  MediaStopRegistry process = new MediaStopRegistry();
  check(process.snapshot().isDone());
  CompletableFuture<Void> oldPlayer = new CompletableFuture<>();
  process.record(oldPlayer);
  // New Activity's idle player is not proof of the retired Activity's completed stop.
  CompletableFuture<Void> replacement = process.record(CompletableFuture.completedFuture(null));
  check(!replacement.isDone());check(!process.snapshot().isDone());
  oldPlayer.complete(null);check(replacement.isDone());check(!replacement.isCompletedExceptionally());
  CompletableFuture<Void> failed = new CompletableFuture<>();process.record(failed);
  failed.completeExceptionally(new IllegalStateException("vendor failure"));
  check(process.snapshot().isCompletedExceptionally());
  check(process.record(CompletableFuture.completedFuture(null)).isCompletedExceptionally());
  System.out.println("Media stop registry: 7 recreation/failed-release assertions passed.");
 }
 private static void check(boolean value){if(!value)throw new AssertionError();}
}
