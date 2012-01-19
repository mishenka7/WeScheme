#lang racket

(require scribble/core
         scribble/html-properties
         scriblib/render-cond
         racket/match
         (for-syntax "extract-docstring.rkt"))

(provide racket-inject-docs
         racket-inject-doc
         inject-css)


(define (inject-css path)
  (cond-element 
   [latex ""]
   [html (make-element (make-style #f (list (make-css-addition path)))
                       '())]
   [text ""]))


(define-syntax (racket-inject-docs stx)
  (syntax-case stx ()
    [(_ binding ...)
     (syntax/loc stx
       (begin
         (racket-inject-doc binding) ...))]))

(define-syntax (racket-inject-doc stx)
  (syntax-case stx ()
    [(_ binding)
     (begin
       (define an-sxml (extract-doc-sexp/id #'binding))
       (with-syntax ([an-sxml an-sxml])
         (syntax/loc stx
           (sxml->element 'an-sxml))))]))

         
(define (sxml->element an-sxml)
  (match an-sxml
    [(list '& 'nbsp)
     'nbsp]
    [(list '& sym)
     sym]

    [(list tag-name (list '@ (list attr-name attr-value) ...) children ...)
     (tagged->element tag-name attr-name attr-value children)]
    
    [(list tag-name children ...)
     (tagged->element tag-name '() '() children)]

    [(? symbol?)
     an-sxml]
    
    [(? string?)
     an-sxml]

    [(? char?)
     (string an-sxml)]))


(define (tagged->element tag-name attr-names attr-values children)
  (cond [(and (eq? tag-name 'a) 
              (equal? attr-names '(href class pltdoc)))
         (define tag-attr (alt-tag (symbol->string tag-name)))
         (define attrs-attr 
           (attributes 
            (list (cons 'href (if (regexp-match #px"^#" (first attr-values))
                                  (first attr-values)
                                  (string-append "http://docs.racket-lang.org/" (first attr-values))))
                  (cons 'class (second attr-values))
                  (cons 'pltdoc (third attr-values)))))
         (define content (map sxml->element children))
         (make-element (make-style #f (list tag-attr attrs-attr))
                       content)]
        [else
         (define tag-attr (alt-tag (symbol->string tag-name)))
         (define attrs-attr (attributes (map cons attr-names attr-values)))
         (define content (map sxml->element children))
         (make-element (make-style #f (list tag-attr attrs-attr))
                       content)]))