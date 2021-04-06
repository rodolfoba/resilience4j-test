package com.github.rodolfoba;

public interface ClientExecutor {

    /**
     * Execute something
     *
     * @return Result
     * @throws RuntimeException Failure
     */
    boolean execute();

    /**
     * Fallback execution of something
     *
     * @return Result
     * @throws RuntimeException Failure
     */
    boolean fallback();

}
