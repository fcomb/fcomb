# fcomb

## About

### What is your company going to make?

---Example
Dropbox synchronizes files across your/your team's computers. It's much better than uploading or email, because it's automatic, integrated into Windows, and fits into the way you already work. There's also a web interface, and the files are securely backed up to Amazon S3. Dropbox is kind of like taking the best elements of subversion, trac and rsync and making them "just work" for the average individual or team. Hackers have access to these tools, but normal people don't.

There are lots of interesting possible features. One is syncing Google Docs/Spreadsheets (or other office web apps) to local .doc and .xls files for offline access, which would be strategically important as few web apps deal with the offline problem.


---Answer
fcomb is a marketplace for api as a microservice. It's easily allow you to build scalable product very quickly with extremly small  



# For each founder, please list: YC username; name; age; year, school, degree, and subject for each degree; email address; personal url (if any); and present employer and title (if any). Put unfinished degrees in parens. List the main contact first. Separate founders with blank lines. Put an asterisk before the name of anyone not able to move to Boston for the summer.  
dhouston; Drew Houston; 24; 2006, MIT, SB computer science; houston AT alum DOT (school i went to) DOT edu; --; Bit9, Inc (went full time to part time 1/07) - project lead/software engineer

Although I'm working with other people on Dropbox, strictly speaking I'm the only founder right now. My friend (redacted), a great hacker, Stanford grad and creator of (redacted) is putting together a Mac port, but can't join as a founder right now as a former cofounder of his started an extremely similar company. My friend and roommate (redacted) from MIT is helping out too, but he works with me at Bit9, and a non-solicit clause in my employment contract prevents me from recruiting him (and the VP Eng explicitly told me not to recruit him.)

In any case, I have several leads, have been networking aggressively, and fully intend to get someone else on board -- either another good hacker or a more sales-oriented guy (e.g. the role Matt fills at Xobni). I'm aware that the odds aren't good for single founders, and would rather work with other people anyway.

# Please tell us in one or two sentences something about each founder that shows a high level of ability.  
Drew - Programming since age 5; startups since age 14; 1600 on SAT; started profitable online SAT prep company in college (accoladeprep.com). For fun last summer reverse engineered the software on a number of poker sites and wrote a real-money playing poker bot (it was about break-even; see screenshot url later in the app.)

# What's new about what you're doing?  
Most small teams have a few basic needs: (1) team members need their important stuff in front of them wherever they are, (2) everyone needs to be working on the latest version of a given document (and ideally can track what's changed), (3) and team data needs to be protected from disaster. There are sync tools (e.g. beinsync, Foldershare), there are backup tools (Carbonite, Mozy), and there are web uploading/publishing tools (box.net, etc.), but there's no good integrated solution.

Dropbox solves all these needs, and doesn't need configuration or babysitting. Put another way, it takes concepts that are proven winners from the dev community (version control, changelogs/trac, rsync, etc.) and puts them in a package that my little sister can figure out (she uses Dropbox to keep track of her high school term papers, and doesn't need to burn CDs or carry USB sticks anymore.)

At a higher level, online storage and local disks are big and cheap. But the internet links in between have been and will continue to be slow in comparison. In "the future", you won't have to move your data around manually. The concept that I'm most excited about is that the core technology in Dropbox -- continuous efficient sync with compression and binary diffs -- is what will get us there.

# What do you understand about your business that other companies in it just don't get?  
Competing products work at the wrong layer of abstraction and/or force the user to constantly think and do things. The "online disk drive" abstraction sucks, because you can't work offline and the OS support is extremely brittle. Anything that depends on manual emailing/uploading (i.e. anything web-based) is a non-starter, because it's basically doing version control in your head. But virtually all competing services involve one or the other.

With Dropbox, you hit "Save", as you normally would, and everything just works, even with large files (thanks to binary diffs).

# What are people forced to do now because what you plan to make doesn't exist yet?  
Email themselves attachments. Upload stuff to online storage sites or use online drives like Xdrive, which don't work on planes. Carry around USB drives, which can be lost, stolen, or break/get bad sectors. Waste time revising the wrong versions of given documents, resulting in Frankendocuments that contain some changes but lose others. My friend Reuben is switching his financial consulting company from a PHP-based CMS to a beta of Dropbox because all they used it for was file sharing. Techies often hack together brittle solutions involving web hosting, rsync, and cron jobs, or entertaining abominations such as those listed in this slashdot article ("Small Office Windows Backup Software" - http://ask.slashdot.org/article.pl?sid=07/01/04/0336246).

# How will you make money?  
The current plan is a freemium approach, where we give away free 1GB accounts and charge for additional storage (maybe ~$5/mo or less for 10GB for individuals and team plans that start at maybe $20/mo.). It's hard to get consumers to pay for things, but fortunately small/medium businesses already pay for solutions that are subsets of what Dropbox does and are harder to use. There will be tiered pricing for business accounts (upper tiers will retain more older versions of documents, have branded extranets for secure file sharing with clients/partners, etc., and an 'enterprise' plan that features, well, a really high price.)

I've already been approached by potential partners/customers asking for an API to programmatically create Dropboxes (e.g. to handle file sharing for Assembla.com, a web site for managing global dev teams). There's a natural synergy between Basecamp-like project mgmt/groupware web apps (for the to-do lists, calendaring, etc.) and Dropbox for file sharing. I've also had requests for an enterprise version that would sit on a company's network (as opposed to my S3 store) for which I could probably charge a lot.

# Who are your competitors, and who might become competitors? Who do you fear most?  
Carbonite and Mozy do a good job with hassle-free backup, and a move into sync would make sense. Sharpcast (venture funded) announced a similar app called Hummingbird, but according to (redacted) they're taking an extraordinarily difficult approach involving NT kernel drivers. Google's coming out with GDrive at some point. Microsoft's Groove does sync and is part of Office 2007, but is very heavyweight and doesn't include any of the web stuff or backup. There are apps like Omnidrive and Titanize but the implementations are buggy or have bad UIs.
 
# For founders who are hackers: what cool things have you built? (Include urls if possible.)  
Accolade Online SAT prep (launched in 2004) (http://www.accoladeprep.com/); a poker bot (here's an old screenshot: https://www.accoladeprep.com/sshot2.gif; it's using play money there but worked with real money too.)

# How long have the founders known one another and how did you meet?  
There's a joke in here somewhere.

# What tools will you use to build your product?  
Python (top to bottom.) sqlite (client), mysql (server). Turbogears (at least until it won't scale.) Amazon EC2 and S3 for serving file data.

# If you've already started working on it, how long have you been working and how many lines of code (if applicable) have you written?  
3 months part time. About ~5KLOC client and ~2KLOC server of python, C++, Cheetah templates, installer scripts, etc.

# If you have an online demo, what's the url?
Here's a screencast that I'll also put up on news.yc:

http://www.getdropbox.com/u/2/screencast%20-%20Copy.html

If you do have a Windows box or two, here's the latest build:

http://www.getdropbox.com/u/2/DropboxInstaller.exe
 
# How long will it take before you have a prototype? A beta? A version you can charge for?  
Prototype - done in Feb. Version I can charge for: 8 weeks maybe? (ed: hahaha)
 
# Which companies would be most likely to buy you?  
Google/MS/Yahoo are all acutely interested in this general space. Google announced GDrive/"Platypus" a long time ago but the release date is uncertain (a friend at Google says the first implementation was this ghetto VBScript/Java thing for internal use only). MS announced Live Drive and bought Foldershare in '05 which does a subset of what Dropbox does. Iron Mountain, Carbonite or Mozy or anyone else dealing with backup for SMBs could also be interested, as none of them have touched the sync problem to date.

In some ways, Dropbox is for arbitrary files what Basecamp is for lightweight project management, and the two would plug together really well (although 37signals doesn't seem like the buying-companies type).

At the end of the day, though, it's an extremely capital-efficient business. We know people are willing to pay for this and just want to put together something that rocks and get it in front of as many people as possible. 

# If one wanted to buy you three months in (August 2007), what's the lowest offer you'd take?  
I'd rather see the idea through, but I'd probably have a hard time turning down $1m after taxes for 6 months of work.

# Why would your project be hard for someone else to duplicate?  
This idea requires executing well in several somewhat orthogonal directions, and missteps in any torpedo the entire product.

For example, there's an academic/theoretical component: designing the protocol and app to behave consistently/recoverably when any power or ethernet cord in the chain could pop out at any time. There's a gross Win32 integration piece (ditto for a Mac port). There's a mostly Linux/Unix-oriented operations/sysadmin and scalability piece. Then there's the web design and UX piece to make things simple and sexy. Most of these hats are pretty different, and if executing in all these directions was easy, a good product/service would already exist.

# Do you have any ideas you consider patentable?  
(redacted)

# What might go wrong? (This is a test of imagination, not confidence.)  
Google might finally unleash GDrive and steal a lot of Dropbox's thunder (especially if this takes place before launch.) In general, the online storage space is extremely noisy, so being marginally better isn't good enough; there has to be a leap in value worthy of writing/blogging/telling friends about. I'll need to bring on cofounder(s) and build a team, which takes time. Other competitors are much better funded; we might need to raise working capital to accelerate growth. There will be the usual growing pains scaling and finding bottlenecks (although I've provisioned load balanced, high availability web apps before.) Acquiring small business customers might be more expensive/take longer than hoped. Prioritizing features and choosing the right market segments to tackle will be hard. Getting love from early adopters will be important, but getting distracted by/releasing late due to frivolous feature requests could be fatal.

# If you're already incorporated, when were you? Who are the shareholders and what percent does each own? If you've had funding, how much, at what valuation(s)?  
Not incorporated

# If you're not incorporated yet, please list the percent of the company you plan to give each founder, and anyone else you plan to give stock to. (This question is as much for you as us.)  
Drew

# If you'll have any major expenses beyond the living costs of your founders, bandwidth, and servers, what will they be?  
None; maybe AdWords.
 
# If by August your startup seems to have a significant (say 20%) chance of making you rich, which of the founders would commit to working on it full-time for the next several years?  
Drew

# Do any founders have other commitments between June and August 2007 inclusive?  
No; I've given notice at Bit9 to work on this full time regardless of YC funding.

# Do any founders have commitments in the future (e.g. have been accepted to grad school), and if so what?  
No. Probably moving to SF in September
 
# Are any of the founders covered by noncompetes or intellectual property agreements that overlap with your project? Will any be working as employees or consultants for anyone else?  
Drew: Some work was done at the Bit9 office; I consulted an attorney and have a signed letter indicating Bit9 has no stake/ownership of any kind in Dropbox

# Was any of your code written by someone who is not one of your founders? If so, how can you safely use it? (Open source is ok of course.)  
No

# If you had any other ideas you considered applying with, feel free to list them. One may be something we've been waiting for.  
One click screen sharing (already done pretty well by Glance); a wiki with version-controlled drawing canvases that let you draw diagrams or mock up UIs (Thinkature is kind of related, but this is more text with canvases interspersed than a shared whiteboard) to help teams get on the same page and spec things out better (we use Visio and Powerpoint at Bit9, which sucks)
 
# Please tell us something surprising or amusing that one of you has discovered. (The answer need not be related to your project.)  
The ridiculous things people name their documents to do versioning, like "proposal v2 good revised NEW 11-15-06.doc", continue to crack me up.

