# License Options for LiteORM

## Scope and conclusion

This note compares licenses that are commonly considered for a Java library distributed
through Maven Central. It is a technical comparison of the license texts, not legal advice.
The project must also confirm that all existing source, documentation, and bundled assets can
legally be relicensed by the copyright holders.

MyBatis 3.5.19 is released under the Apache License, Version 2.0: the tagged source tree
contains the Apache 2.0 license text, and the MyBatis parent POM declares `Apache License,
Version 2.0` as its Maven license metadata ([MyBatis LICENSE](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/LICENSE),
[MyBatis parent POM](https://github.com/mybatis/parent/blob/master/pom.xml#L36-L42)). Apache-2.0
is therefore the closest precedent if LiteORM wants a permissive license with an express patent
grant and straightforward Maven consumption.

## Comparison

| License | Redistribution and modification | Copyleft / downstream obligation | Patent / trademark notes | Typical Maven-library impact |
| --- | --- | --- | --- | --- |
| **Apache-2.0** | Permits use, modification, distribution, and sublicensing in source or binary form, subject to preserving the license, notices, and change notices. | Permissive: modifications to a library do not have to be published under Apache-2.0. | Express patent license; patent litigation can terminate the patent grant. The text does not grant trademark rights. | Strong fit for a reusable Java library and compatible with proprietary applications; requires NOTICE/license handling where applicable. [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **MIT** | Permits use, copy, modification, merge, publication, distribution, sublicensing, and sale, provided the copyright and permission notice are retained. | Permissive; no source-sharing requirement. | No express patent grant and no trademark license in the short license text. | Very simple downstream obligations and broad adoption; weaker explicit patent protection than Apache-2.0. [MIT](https://opensource.org/license/mit) |
| **BSD-3-Clause** | Permits redistribution and use in source and binary forms with or without modification, subject to retaining notices and conditions. | Permissive; no source-sharing requirement. | No express patent grant. Includes a non-endorsement clause restricting use of the names of contributors/organization to endorse products. | Similar to MIT, with a somewhat more explicit redistribution/non-endorsement text. [BSD-3-Clause](https://opensource.org/license/bsd-3-clause) |
| **MPL-2.0** | Permits use, modification, distribution, and combination with other code. Covered source files remain under MPL-2.0 when distributed in source form. | Weak/file-level copyleft: modifications to MPL-covered files must remain MPL-2.0, while separate files may use another license. | Includes an express patent license and patent-termination provision; trademark rights are not granted. | Allows proprietary applications while requiring source availability for modified covered files; more compliance work than permissive licenses. [MPL-2.0](https://www.mozilla.org/en-US/MPL/2.0/) |
| **LGPL-3.0** | Allows copying, modification, and distribution, including linking with a larger work under different terms, subject to LGPL/GPL conditions and relinkability requirements. | Library-level copyleft: modifications to the LGPL library remain LGPL/GPL; applications may generally remain under other terms when the relinking conditions are met. | GPLv3 patent and anti-tivoization provisions apply through the LGPL terms. | Can work for a library, but distributors must satisfy notices, source, and relinking requirements; legal review is advisable for shaded/modified distributions. [LGPL-3.0](https://www.gnu.org/licenses/lgpl-3.0.html) |
| **GPL-3.0** | Permits use, modification, and redistribution only under GPLv3 conditions, including corresponding-source obligations for conveyed binaries. | Strong whole-work copyleft: a combined/derivative distributed work generally must be licensed under GPLv3. | Express patent license, patent-termination provisions, and anti-tivoization requirements. | Usually unsuitable for a general-purpose library intended for proprietary applications because downstream distributed combined works inherit GPL obligations. [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) |

## Practical selection guidance

For LiteORM's stated goal—a compile-time Java/JDBC library that should be usable from both
open-source and proprietary applications—the least-surprising choices are Apache-2.0, MIT, or
BSD-3-Clause. Apache-2.0 offers the clearest explicit patent grant and is consistent with the
MyBatis ecosystem. MIT minimizes notice friction but does not contain an express patent grant.
BSD-3-Clause adds a non-endorsement condition without adding an express patent grant.

MPL-2.0 is appropriate only if the project intentionally wants file-level copyleft. LGPL-3.0
and GPL-3.0 impose materially stronger redistribution and source/relinking obligations and
should not be selected merely because LiteORM is a library.

Before publishing, maintainers should choose one license, add its complete text to the repository,
declare matching `<licenses>` metadata in every published Maven POM (or the parent POM), and
preserve third-party notices. A license choice must be approved by the copyright holders.

