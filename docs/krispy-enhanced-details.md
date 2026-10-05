# Krispy enhanced detail screens

Enable **Enhanced detail screens** under **Settings > Customization**. This
defaults to off and is independent of Enhanced home screen.

Movies, series, seasons, and episodes use a separate detail layout with a bold
title on the left, compact metadata, genres and season count, a four-line
expandable description, the existing actions, and larger artwork on the right.
Focus uses Krispy's blue accent. Additional actions remain in Other options.
Episode, season, cast, and related rows retain their existing behavior.

A local sharp backdrop uses the existing artwork loader and cache, with dark
fades behind text and lower rows. Item artwork is tried before parent artwork;
missing or failed artwork uses a plain dark background. This local layer does
not alter the global backdrop preference or Enhanced Home.

Metadata comes from Jellyfin. Year ranges, network or studio, runtime, ratings,
genres, and season count appear only when available. Music, people, and live TV
keep the classic layout. Disabling the option restores classic details, and
preference changes refresh the currently open detail screen.

Validation: the debug and signed release builds and all 170 unit tests passed, including opt-out,
item-type isolation, missing metadata, and the existing Home artwork and
metadata helpers. On the .50 development TV, movies and series displayed the
new layout, both Home/detail toggle combinations worked independently, turning
details off restored the classic layout, four-line descriptions fit, and D-pad
navigation into episode rows returned to the previously focused action.
Leaving movie details also retained the Home selection.

Additional manual acceptance checks: an ellipsized description opens the full
description popup, missing artwork stays dark, and returning from playback
retains navigation. The existing popup and playback actions are reused.
