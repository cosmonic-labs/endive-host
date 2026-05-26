use std::io::{self, Read, Write};

use pulldown_cmark::{html, Options, Parser};

fn main() -> io::Result<()> {
    let mut input = String::new();
    io::stdin().read_to_string(&mut input)?;

    let mut opts = Options::empty();
    opts.insert(Options::ENABLE_TABLES);
    opts.insert(Options::ENABLE_STRIKETHROUGH);
    opts.insert(Options::ENABLE_TASKLISTS);

    let parser = Parser::new_ext(&input, opts);
    let mut html_out = String::new();
    html::push_html(&mut html_out, parser);

    io::stdout().write_all(html_out.as_bytes())?;
    Ok(())
}
