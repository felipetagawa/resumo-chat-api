package com.soften.support.gemini_resumo.models.dtos;

public record TipResponseDto(
        SummaryDto summary,
        String problemDetected,
        String moduleDetected,
        Integer SimilarTagsFound,
        Integer solutionsAnalyzed,
        java.util.List<String> tips,
        String status
) {
    public static TipResponseDTOBuilder builder() {
        return new TipResponseDTOBuilder();
    }

    public static class TipResponseDTOBuilder {
        private SummaryDto summary;
        private String problemDetected;
        private String moduleDetected;
        private Integer SimilarTagsFound;
        private Integer solutionsAnalyzed;
        private java.util.List<String> tips;
        private String status;

        public TipResponseDTOBuilder() {
            this.tips = new java.util.ArrayList<>();
        }

        public TipResponseDTOBuilder summary(SummaryDto summary) {
            this.summary = summary;
            return this;
        }

        public TipResponseDTOBuilder problemDetected(String problemDetected) {
            this.problemDetected = problemDetected;
            return this;
        }

        public TipResponseDTOBuilder moduleDetected(String moduleDetected) {
            this.moduleDetected = moduleDetected;
            return this;
        }

        public TipResponseDTOBuilder SimilarTagsFound(Integer SimilarTagsFound) {
            this.SimilarTagsFound = SimilarTagsFound;
            return this;
        }

        public TipResponseDTOBuilder solutionsAnalyzed(Integer solutionsAnalyzed) {
            this.solutionsAnalyzed = solutionsAnalyzed;
            return this;
        }

        public TipResponseDTOBuilder tips(java.util.List<String> tips) {
            this.tips = tips != null ? tips : new java.util.ArrayList<>();
            return this;
        }

        public TipResponseDTOBuilder addTips(String tip) {
            if (this.tips == null) {
                this.tips = new java.util.ArrayList<>();
            }
            this.tips.add(tip);
            return this;
        }

        public TipResponseDTOBuilder status(String status) {
            this.status = status;
            return this;
        }

        public TipResponseDto build() {
            return new TipResponseDto(
                    summary,
                    problemDetected,
                    moduleDetected,
                    SimilarTagsFound,
                    solutionsAnalyzed,
                    tips,
                    status
            );
        }
    }
}
